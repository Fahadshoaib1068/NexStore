package com.example.store.model;

public class VideoProcessed {
    private Integer processed_id;
    private Integer video_id;
    private String  quality;
    private String  file_path;
    private String  processed_at;

    public VideoProcessed() {}

    public VideoProcessed(Integer processed_id, Integer video_id,
                          String quality, String file_path, String processed_at) {
        this.processed_id = processed_id;
        this.video_id     = video_id;
        this.quality      = quality;
        this.file_path    = file_path;
        this.processed_at = processed_at;
    }

    public Integer getProcessed_id() { return processed_id; }
    public Integer getVideo_id()     { return video_id; }
    public String  getQuality()      { return quality; }
    public String  getFile_path()    { return file_path; }
    public String  getProcessed_at() { return processed_at; }

    public void setProcessed_id(Integer processed_id) { this.processed_id = processed_id; }
    public void setVideo_id(Integer video_id)         { this.video_id = video_id; }
    public void setQuality(String quality)            { this.quality = quality; }
    public void setFile_path(String file_path)        { this.file_path = file_path; }
    public void setProcessed_at(String processed_at)  { this.processed_at = processed_at; }
}