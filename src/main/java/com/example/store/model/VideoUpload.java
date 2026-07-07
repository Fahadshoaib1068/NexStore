package com.example.store.model;

public class VideoUpload {
    private Integer video_id;
    private String  original_name;
    private String  file_path;
    private String  status;
    private String  uploaded_at;
    private String  uploaded_by;

    public VideoUpload() {}

    public VideoUpload(Integer video_id, String original_name, String file_path,
                       String status, String uploaded_at, String uploaded_by) {
        this.video_id      = video_id;
        this.original_name = original_name;
        this.file_path     = file_path;
        this.status        = status;
        this.uploaded_at   = uploaded_at;
        this.uploaded_by   = uploaded_by;
    }

    public Integer getVideo_id()      { return video_id; }
    public String  getOriginal_name() { return original_name; }
    public String  getFile_path()     { return file_path; }
    public String  getStatus()        { return status; }
    public String  getUploaded_at()   { return uploaded_at; }
    public String  getUploaded_by()   { return uploaded_by; }

    public void setVideo_id(Integer video_id)           { this.video_id = video_id; }
    public void setOriginal_name(String original_name)  { this.original_name = original_name; }
    public void setFile_path(String file_path)          { this.file_path = file_path; }
    public void setStatus(String status)                { this.status = status; }
    public void setUploaded_at(String uploaded_at)      { this.uploaded_at = uploaded_at; }
    public void setUploaded_by(String uploaded_by)      { this.uploaded_by = uploaded_by; }
}