package com.example.aip.platform.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

@Entity
public class Document {

    @Id
    private String id;
    private String datasetId;
    private String name;
    @Lob
    private String content;
    @Enumerated(EnumType.STRING)
    private DocumentStatus status;
    private Integer segmentCount;
    private String jobId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }
    public Integer getSegmentCount() { return segmentCount; }
    public void setSegmentCount(Integer segmentCount) { this.segmentCount = segmentCount; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
}
