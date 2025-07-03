package com.javallm.controllers.dto;

public class FileDto {
    public static class FileListResponse {
        private String fileId;
        private String filename;
        private String path;
        private String uploadedAt;

        public FileListResponse() {
        }

        public FileListResponse(String fileId, String filename, String path, String uploadedAt) {
            this.fileId = fileId;
            this.filename = filename;
            this.path = path;
            this.uploadedAt = uploadedAt;
        }

        public String getFileId() {
            return fileId;
        }

        public void setFileId(String fileId) {
            this.fileId = fileId;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getUploadedAt() {
            return uploadedAt;
        }
    }

    public static class FileDeleteResponse {
        private String message = "File deleted successfully";
        private String fileId;

        public FileDeleteResponse() {
        }

        public FileDeleteResponse(String fileId) {
            this.fileId = fileId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getFileId() {
            return fileId;
        }

        public void setFileId(String fileId) {
            this.fileId = fileId;
        }

    }
}
