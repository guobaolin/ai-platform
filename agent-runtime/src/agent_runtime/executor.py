"""Agent execution engine: parse contract -> call control plane -> emit events.

No business rules. Status transitions are pushed via the `set_status` callback
owned by the API layer's in-flight registry.
"""

import asyncio
import json
import logging
from collections.abc import Callable
from typing import Any

from . import control_plane_client, events

logger = logging.getLogger("agent_runtime.executor")

CancelCheck = Callable[[], bool]


async def run_agent(
    app_id: str,
    tenant_id: str,
    run_id: str,
    query: str,
    set_status: Callable[[str], None],
) -> None:
    """Full run lifecycle. Never raises: failures are reported as RUN_ERROR events."""
    set_status("RUNNING")
    await events.emit(run_id, "RUN_STARTED", {"appId": app_id, "tenantId": tenant_id})

    # Cancel channel: PUBLISH run:commands:{run_id} {"action":"cancel"} interrupts us.
    pubsub, _channel = await events.subscribe_commands(run_id)
    watcher = (
        asyncio.create_task(_watch_cancel(pubsub, asyncio.current_task())) if pubsub else None
    )
    try:
        await _execute(app_id, run_id, query, set_status)
    except asyncio.CancelledError:
        set_status("CANCELLED")
        await events.emit(run_id, "RUN_ERROR", {"code": "CANCELLED", "message": "run cancelled"})
    except Exception as exc:  # noqa: BLE001 -- report, do not crash the process
        logger.exception("run %s failed", run_id)
        set_status("ERROR")
        await events.emit(run_id, "RUN_ERROR", {"code": "RUNTIME_ERROR", "message": str(exc)})
    finally:
        if watcher is not None:
            watcher.cancel()
        if pubsub is not None:
            try:
                await pubsub.close()
            except Exception:  # noqa: BLE001
                pass


async def _execute(app_id: str, run_id: str, query: str, set_status: Callable[[str], None]) -> None:
    # 1. Fetch runtime config from control plane (credentials stay server-side).
    try:
        config: dict[str, Any] = await control_plane_client.fetch_app_config(app_id)
    except Exception as exc:  # noqa: BLE001
        set_status("ERROR")
        await events.emit(run_id, "RUN_ERROR", {"code": "CONFIG_FETCH_FAILED", "message": str(exc)})
        return

    system_prompt: str = config.get("systemPrompt", "")
    model: str = str((config.get("modelConfig") or {}).get("model", "unknown"))

    # 2. Stream tokens through the control-plane LLM proxy, emitting each chunk.
    messages = [{"role": "system", "content": system_prompt}, {"role": "user", "content": query}]
    prompt_len = sum(len(m["content"]) for m in messages)
    answer_parts: list[str] = []
    async for token in control_plane_client.stream_llm_tokens(app_id, run_id, messages):
        answer_parts.append(token)
        await events.emit(run_id, "LLM_TOKEN", {"text": token})
    answer = "".join(answer_parts)

    # 3. Usage: prototype estimate ~4 chars per token; real billing happens in control plane.
    await events.emit(
        run_id,
        "USAGE",
        {"model": model, "promptTokens": prompt_len // 4, "completionTokens": len(answer) // 4},
    )
    await events.emit(run_id, "RUN_FINISHED", {"answer": answer})
    set_status("FINISHED")


async def _watch_cancel(pubsub: Any, task: asyncio.Task) -> None:
    """Poll the command channel; cancel the run task on {"action":"cancel"}."""
    try:
        while True:
            msg = await pubsub.get_message(ignore_subscribe_messages=True, timeout=1.0)
            if msg and msg.get("type") == "message":
                try:
                    cmd = json.loads(msg["data"])
                except (json.JSONDecodeError, TypeError):
                    continue
                if cmd.get("action") == "cancel":
                    logger.info("cancel command received")
                    task.cancel()
                    return
    except asyncio.CancelledError:
        raise
    except Exception as exc:  # noqa: BLE001 -- subscription loss must not kill the run
        logger.warning("cancel watcher stopped: %s", exc)
