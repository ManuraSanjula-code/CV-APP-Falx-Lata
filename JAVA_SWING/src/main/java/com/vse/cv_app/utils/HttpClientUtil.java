package com.vse.cv_app.utils;

import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpClientUtil {

    public static class UploadResult {
        public int successCount;
        public int errorCount;
        public String message;

        public UploadResult(int success, int errors, String msg) {
            this.successCount = success;
            this.errorCount = errors;
            this.message = msg;
        }
    }

    public static class UpdateResult {
        public boolean success;
        public String message;

        public UpdateResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    public static class IndexesResult {
        public Map<String, List<String>> indexesData;
        public String errorMessage;

        public IndexesResult(Map<String, List<String>> data, String error) {
            this.indexesData = data;
            this.errorMessage = error;
        }
    }

    public static class DeleteResult {
        public boolean success;
        public String message;
        public String errorMessage;

        public DeleteResult(boolean success, String message, String error) {
            this.success = success;
            this.message = message;
            this.errorMessage = error;
        }
    }

    public static class SearchResult {
        public String jsonResponse;
        public String errorMessage;

        public SearchResult(String json, String error) {
            this.jsonResponse = json;
            this.errorMessage = error;
        }
    }

    public static class CVDetailsResult {
        public String jsonResponse;
        public String errorMessage;

        public CVDetailsResult(String json, String error) {
            this.jsonResponse = json;
            this.errorMessage = error;
        }
    }

    public static class LoginResult {
        public boolean success;
        public String token;
        public String errorMessage;

        public LoginResult(boolean success, String token, String error) {
            this.success = success;
            this.token = token;
            this.errorMessage = error;
        }
    }

    public static class AuditLogResult {
        public List<JSONObject> logs;
        public int page;
        public int perPage;
        public int total;
        public int totalPages;
        public String errorMessage;

        public AuditLogResult(List<JSONObject> logs, int page, int perPage, int total, int totalPages, String error) {
            this.logs = logs;
            this.page = page;
            this.perPage = perPage;
            this.total = total;
            this.totalPages = totalPages;
            this.errorMessage = error;
        }
    }

    public static class AuditDetailResult {
        public JSONObject auditLog;
        public JSONObject metadata;
        public String errorMessage;

        public AuditDetailResult(JSONObject auditLog, JSONObject metadata, String error) {
            this.auditLog = auditLog;
            this.metadata = metadata;
            this.errorMessage = error;
        }
    }

    public static class DateRangeResult {
        public String earliestLog;
        public String latestLog;
        public int totalLogs;
        public String errorMessage;

        public DateRangeResult(String earliest, String latest, int total, String error) {
            this.earliestLog = earliest;
            this.latestLog = latest;
            this.totalLogs = total;
            this.errorMessage = error;
        }
    }

    public static class FilterOptionsResult {
        public List<String> actions;
        public List<String> users;
        public String errorMessage;

        public FilterOptionsResult(List<String> actions, List<String> users, String error) {
            this.actions = actions;
            this.users = users;
            this.errorMessage = error;
        }
    }

    // New class for search parameters
    public static class SearchParameters {
        public String query;
        public int page;
        public int perPage;
        public String dateFrom;  // Format: YYYY-MM-DD
        public String dateTo;    // Format: YYYY-MM-DD
        public String sortBy;    // upload_date, name, filename
        public String sortOrder; // asc, desc
        public String logic;     // and, or

        public SearchParameters() {
            this.query = "";
            this.page = 1;
            this.perPage = 10;
            this.dateFrom = null;
            this.dateTo = null;
            this.sortBy = "upload_date";
            this.sortOrder = "desc";
            this.logic = "and";
        }

        public SearchParameters(String query, int page) {
            this();
            this.query = query;
            this.page = page;
        }
    }

    // New class for date presets
    public static class DatePreset {
        public static final String TODAY = "today";
        public static final String YESTERDAY = "yesterday";
        public static final String LAST_7_DAYS = "last_7_days";
        public static final String LAST_30_DAYS = "last_30_days";
        public static final String LAST_3_MONTHS = "last_3_months";
        public static final String LAST_6_MONTHS = "last_6_months";
        public static final String LAST_YEAR = "last_year";

        public static SearchParameters applyPreset(SearchParameters params, String preset) {
            LocalDate today = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            switch (preset) {
                case TODAY:
                    params.dateFrom = today.format(formatter);
                    params.dateTo = today.format(formatter);
                    break;
                case YESTERDAY:
                    LocalDate yesterday = today.minusDays(1);
                    params.dateFrom = yesterday.format(formatter);
                    params.dateTo = yesterday.format(formatter);
                    break;
                case LAST_7_DAYS:
                    params.dateFrom = today.minusDays(7).format(formatter);
                    params.dateTo = today.format(formatter);
                    break;
                case LAST_30_DAYS:
                    params.dateFrom = today.minusDays(30).format(formatter);
                    params.dateTo = today.format(formatter);
                    break;
                case LAST_3_MONTHS:
                    params.dateFrom = today.minusDays(90).format(formatter);
                    params.dateTo = today.format(formatter);
                    break;
                case LAST_6_MONTHS:
                    params.dateFrom = today.minusDays(180).format(formatter);
                    params.dateTo = today.format(formatter);
                    break;
                case LAST_YEAR:
                    params.dateFrom = today.minusDays(365).format(formatter);
                    params.dateTo = today.format(formatter);
                    break;
            }
            return params;
        }
    }

    public static LoginResult login(String serverUrl, String username, String password) {
        String loginUrl = serverUrl + "/login";
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost loginRequest = new HttpPost(loginUrl);
            loginRequest.setHeader("Content-Type", "application/json");

            JSONObject jsonPayload = new JSONObject();
            jsonPayload.put("username", username);
            jsonPayload.put("password", password);
            StringEntity entity = new StringEntity(jsonPayload.toString(), ContentType.APPLICATION_JSON);
            loginRequest.setEntity(entity);

            ClassicHttpResponse response = httpClient.execute(loginRequest);
            int statusCode = response.getCode();
            String responseString = EntityUtils.toString(response.getEntity());

            if (statusCode == 200) {
                JSONObject jsonResponse = new JSONObject(responseString);
                if (jsonResponse.has("token")) {
                    return new LoginResult(true, jsonResponse.getString("token"), null);
                } else {
                    return new LoginResult(false, null, "Login successful but no token received.");
                }
            } else {
                String errorMsg = "Login failed (HTTP " + statusCode + ")";
                try {
                    JSONObject errorJson = new JSONObject(responseString);
                    if (errorJson.has("message")) {
                        errorMsg = errorJson.getString("message");
                    }
                } catch (Exception parseEx) {
                    errorMsg += ": " + responseString;
                }
                return new LoginResult(false, null, errorMsg);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new LoginResult(false, null, "Network error: " + e.getMessage());
        }
    }

    public static IndexesResult fetchIndexes(String serverUrl) {
        String indexesUrl = serverUrl + "/api/indexes";
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet indexesRequest = new HttpGet(indexesUrl);
            ClassicHttpResponse response = httpClient.execute(indexesRequest);
            int statusCode = response.getCode();
            String responseString = EntityUtils.toString(response.getEntity());

            if (statusCode == 200) {
                JSONObject jsonResponse = new JSONObject(responseString);
                Map<String, List<String>> indexesMap = new HashMap<>();

                for (String categoryKey : jsonResponse.keySet()) {
                    JSONArray itemsArray = jsonResponse.getJSONArray(categoryKey);
                    List<String> itemsList = new ArrayList<>();

                    for (int i = 0; i < itemsArray.length(); i++) {
                        Object itemObj = itemsArray.get(i);
                        if (itemObj instanceof String) {
                            itemsList.add((String) itemObj);
                        } else {
                            itemsList.add(itemObj.toString());
                        }
                    }
                    indexesMap.put(categoryKey, itemsList);
                }

                return new IndexesResult(indexesMap, null);
            } else {
                return new IndexesResult(null, "Server Error (" + statusCode + "): " + responseString);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new IndexesResult(null, "Network Error: " + e.getMessage());
        }
    }

    // Updated search method with date filtering support
    public static SearchResult searchCVs(String serverUrl, SearchParameters params) {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(serverUrl).append("/api/search");

        // Build query parameters
        List<String> queryParams = new ArrayList<>();

        if (params.query != null && !params.query.trim().isEmpty()) {
            queryParams.add("q=" + java.net.URLEncoder.encode(params.query.trim(), java.nio.charset.StandardCharsets.UTF_8));
        }

        queryParams.add("page=" + params.page);
        queryParams.add("per_page=" + params.perPage);

        if (params.dateFrom != null && !params.dateFrom.trim().isEmpty()) {
            queryParams.add("date_from=" + java.net.URLEncoder.encode(params.dateFrom.trim(), java.nio.charset.StandardCharsets.UTF_8));
        }

        if (params.dateTo != null && !params.dateTo.trim().isEmpty()) {
            queryParams.add("date_to=" + java.net.URLEncoder.encode(params.dateTo.trim(), java.nio.charset.StandardCharsets.UTF_8));
        }

        if (params.sortBy != null && !params.sortBy.trim().isEmpty()) {
            queryParams.add("sort_by=" + java.net.URLEncoder.encode(params.sortBy.trim(), java.nio.charset.StandardCharsets.UTF_8));
        }

        if (params.sortOrder != null && !params.sortOrder.trim().isEmpty()) {
            queryParams.add("sort_order=" + java.net.URLEncoder.encode(params.sortOrder.trim(), java.nio.charset.StandardCharsets.UTF_8));
        }

        if (params.logic != null && !params.logic.trim().isEmpty()) {
            queryParams.add("logic=" + java.net.URLEncoder.encode(params.logic.trim(), java.nio.charset.StandardCharsets.UTF_8));
        }

        // Add query parameters to URL
        if (!queryParams.isEmpty()) {
            urlBuilder.append("?").append(String.join("&", queryParams));
        }

        String searchUrl = urlBuilder.toString();

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet searchRequest = new HttpGet(searchUrl);
            searchRequest.setHeader("Accept", "application/json");

            ClassicHttpResponse response = httpClient.execute(searchRequest);
            int statusCode = response.getCode();
            String responseString = EntityUtils.toString(response.getEntity());

            if (statusCode == 200) {
                return new SearchResult(responseString, null);
            } else {
                return new SearchResult(null, "Search Error (" + statusCode + "): " + responseString);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new SearchResult(null, "Network Error: " + e.getMessage());
        }
    }

    // Backward compatibility method
    public static SearchResult searchCVs(String serverUrl, String query, int page) {
        SearchParameters params = new SearchParameters(query, page);
        return searchCVs(serverUrl, params);
    }

    // New method to get filter options including date presets
    public static SearchResult getFilterOptions(String serverUrl) {
        String filterUrl = serverUrl + "/api/filter_options";
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet filterRequest = new HttpGet(filterUrl);
            filterRequest.setHeader("Accept", "application/json");

            ClassicHttpResponse response = httpClient.execute(filterRequest);
            int statusCode = response.getCode();
            String responseString = EntityUtils.toString(response.getEntity());

            if (statusCode == 200) {
                return new SearchResult(responseString, null);
            } else {
                return new SearchResult(null, "Filter Options Error (" + statusCode + "): " + responseString);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new SearchResult(null, "Network Error: " + e.getMessage());
        }
    }

    // New method to get recent uploads
    public static SearchResult getRecentUploads(String serverUrl, int days, int page, int perPage) {
        String recentUrl = serverUrl + "/api/recent_uploads?days=" + days + "&page=" + page + "&per_page=" + perPage;
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet recentRequest = new HttpGet(recentUrl);
            recentRequest.setHeader("Accept", "application/json");

            ClassicHttpResponse response = httpClient.execute(recentRequest);
            int statusCode = response.getCode();
            String responseString = EntityUtils.toString(response.getEntity());

            if (statusCode == 200) {
                return new SearchResult(responseString, null);
            } else {
                return new SearchResult(null, "Recent Uploads Error (" + statusCode + "): " + responseString);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new SearchResult(null, "Network Error: " + e.getMessage());
        }
    }

    public static CVDetailsResult getCVDetails(String serverUrl, String cvId) {
        String viewUrl = serverUrl + "/api/view/" + cvId;
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet viewRequest = new HttpGet(viewUrl);
            ClassicHttpResponse response = httpClient.execute(viewRequest);
            int statusCode = response.getCode();
            String responseString = EntityUtils.toString(response.getEntity());

            if (statusCode == 200) {
                return new CVDetailsResult(responseString, null);
            } else {
                return new CVDetailsResult(null, "View Error (" + statusCode + "): " + responseString);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new CVDetailsResult(null, "Network Error: " + e.getMessage());
        }
    }

    public static UploadResult uploadFilesWithToken(String serverUrl, List<File> files, String jwtToken) {
        String uploadUrl = serverUrl + "/upload";
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost uploadFile = new HttpPost(uploadUrl);
            if (jwtToken != null && !jwtToken.isEmpty()) {
                uploadFile.setHeader("Authorization", "Bearer " + jwtToken);
            }
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            for (File file : files) {
                builder.addBinaryBody("files[]", file, ContentType.APPLICATION_OCTET_STREAM, file.getName());
            }
            HttpEntity multipart = builder.build();
            uploadFile.setEntity(multipart);
            ClassicHttpResponse response = httpClient.execute(uploadFile);
            int statusCode = response.getCode();
            String responseString = EntityUtils.toString(response.getEntity());
            if (statusCode == 200) {
                JSONObject jsonResponse = new JSONObject(responseString);
                int successCount = jsonResponse.getInt("success_count");
                int errorCount = jsonResponse.getInt("error_count");
                StringBuilder message = new StringBuilder();
                message.append("Uploaded: ").append(successCount).append(", Errors: ").append(errorCount);
                if (jsonResponse.has("errors") && jsonResponse.getJSONArray("errors").length() > 0) {
                    message.append("\nErrors:\n");
                    for (int i = 0; i < Math.min(5, jsonResponse.getJSONArray("errors").length()); i++) {
                        JSONObject error = jsonResponse.getJSONArray("errors").getJSONObject(i);
                        message.append("- ").append(error.getString("filename")).append(": ").append(error.getString("error")).append("\n");
                    }
                }
                return new UploadResult(successCount, errorCount, message.toString());
            } else {
                return new UploadResult(0, files.size(), "Server Error (" + statusCode + "): " + responseString);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new UploadResult(0, files.size(), "Network Error: " + e.getMessage());
        }
    }

    public static UpdateResult updateCVData(String serverUrl, String cvId, String jsonData, String jwtToken) {
        String updateUrl = serverUrl + "/api/view/" + cvId;
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPut updateRequest = new HttpPut(updateUrl);
            updateRequest.setHeader("Content-Type", "application/json");
            if (jwtToken != null && !jwtToken.isEmpty()) {
                updateRequest.setHeader("Authorization", "Bearer " + jwtToken);
            }
            StringEntity entity = new StringEntity(jsonData, ContentType.APPLICATION_JSON);
            updateRequest.setEntity(entity);
            ClassicHttpResponse response = httpClient.execute(updateRequest);
            int statusCode = response.getCode();
            String responseString = EntityUtils.toString(response.getEntity());
            if (statusCode == 200) {
                try {
                    JSONObject jsonResponse = new JSONObject(responseString);
                    String message = jsonResponse.optString("message", "CV updated successfully");
                    return new UpdateResult(true, message);
                } catch (Exception e) {
                    return new UpdateResult(true, "CV updated successfully (response parsing failed)");
                }
            } else {
                String errorMessage = "Server Error (" + statusCode + "): " + responseString;
                return new UpdateResult(false, errorMessage);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new UpdateResult(false, "Network Error: " + e.getMessage());
        }
    }

    public static DeleteResult deleteCV(String serverUrl, String cvId, String jwtToken) {
        String deleteUrl = serverUrl + "/api/cv/" + cvId;
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpDelete deleteRequest = new HttpDelete(deleteUrl);
            if (jwtToken != null && !jwtToken.isEmpty()) {
                deleteRequest.setHeader("Authorization", "Bearer " + jwtToken);
            }
            ClassicHttpResponse response = httpClient.execute(deleteRequest);
            int statusCode = response.getCode();
            String responseString = EntityUtils.toString(response.getEntity());
            if (statusCode == 200) {
                try {
                    JSONObject jsonResponse = new JSONObject(responseString);
                    String message = jsonResponse.optString("message", "CV deleted successfully.");
                    return new DeleteResult(true, message, null);
                } catch (Exception jsonEx) {
                    System.err.println("Warning: Could not parse JSON response for delete: " + responseString);
                    return new DeleteResult(true, "CV deleted (response: " + responseString + ")", null);
                }
            } else {
                String errorMessage = "Delete Error (" + statusCode + "): " + responseString;
                System.err.println(errorMessage);
                return new DeleteResult(false, null, errorMessage);
            }
        } catch (Exception e) {
            String networkError = "Network Error during delete: " + e.getMessage();
            e.printStackTrace();
            return new DeleteResult(false, null, networkError);
        }
    }

    public static AuditDetailResult fetchAuditLogById(String serverUrl, String logId) {
        String logUrl = serverUrl + "/api/audit_logs/" + logId;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet logRequest = new HttpGet(logUrl);
            ClassicHttpResponse response = httpClient.execute(logRequest);
            int statusCode = response.getCode();
            String responseString = EntityUtils.toString(response.getEntity());

            if (statusCode == 200) {
                JSONObject jsonResponse = new JSONObject(responseString);

                JSONObject auditLog = jsonResponse.optJSONObject("audit_log");
                JSONObject metadata = jsonResponse.optJSONObject("metadata");

                return new AuditDetailResult(auditLog, metadata, null);
            } else {
                return new AuditDetailResult(null, null, "Server Error (" + statusCode + "): " + responseString);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new AuditDetailResult(null, null, "Network Error: " + e.getMessage());
        }
    }

    public static AuditLogResult fetchAuditLogs(String serverUrl, int page, int perPage,
                                                String userFilter, String actionFilter,
                                                String startDate, String endDate) {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(serverUrl).append("/api/audit_logs?page=").append(page).append("&per_page=").append(perPage);

        if (userFilter != null && !userFilter.trim().isEmpty()) {
            urlBuilder.append("&user=").append(java.net.URLEncoder.encode(userFilter.trim(), java.nio.charset.StandardCharsets.UTF_8));
        }

        if (actionFilter != null && !actionFilter.trim().isEmpty()) {
            urlBuilder.append("&action=").append(java.net.URLEncoder.encode(actionFilter.trim(), java.nio.charset.StandardCharsets.UTF_8));
        }

        if (startDate != null && !startDate.trim().isEmpty()) {
            urlBuilder.append("&start_date=").append(java.net.URLEncoder.encode(startDate.trim(), java.nio.charset.StandardCharsets.UTF_8));
        }

        if (endDate != null && !endDate.trim().isEmpty()) {
            urlBuilder.append("&end_date=").append(java.net.URLEncoder.encode(endDate.trim(), java.nio.charset.StandardCharsets.UTF_8));
        }

        String logsUrl = urlBuilder.toString();

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet logsRequest = new HttpGet(logsUrl);
            ClassicHttpResponse response = httpClient.execute(logsRequest);
            int statusCode = response.getCode();
            String responseString = EntityUtils.toString(response.getEntity());

            if (statusCode == 200) {
                JSONObject jsonResponse = new JSONObject(responseString);

                int returnedPage = jsonResponse.getInt("page");
                int returnedPerPage = jsonResponse.getInt("per_page");
                int total = jsonResponse.getInt("total");
                int totalPages = jsonResponse.getInt("total_pages");

                JSONArray logsArray = jsonResponse.getJSONArray("logs");
                List<JSONObject> logsList = new ArrayList<>();
                for (int i = 0; i < logsArray.length(); i++) {
                    logsList.add(logsArray.getJSONObject(i));
                }

                return new AuditLogResult(logsList, returnedPage, returnedPerPage, total, totalPages, null);
            } else {
                return new AuditLogResult(null, page, perPage, 0, 0, "Server Error (" + statusCode + "): " + responseString);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new AuditLogResult(null, page, perPage, 0, 0, "Network Error: " + e.getMessage());
        }
    }

    public static AuditLogResult fetchAuditLogs(String serverUrl, int page, int perPage) {
        return fetchAuditLogs(serverUrl, page, perPage, null, null, null, null);
    }

    public static DateRangeResult fetchAuditDateRange(String serverUrl) {
        String dateRangeUrl = serverUrl + "/api/audit_logs/date_range";

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet dateRangeRequest = new HttpGet(dateRangeUrl);
            ClassicHttpResponse response = httpClient.execute(dateRangeRequest);
            int statusCode = response.getCode();
            String responseString = EntityUtils.toString(response.getEntity());

            if (statusCode == 200) {
                JSONObject jsonResponse = new JSONObject(responseString);

                String earliest = jsonResponse.optString("earliest_log", null);
                String latest = jsonResponse.optString("latest_log", null);
                int total = jsonResponse.optInt("total_logs", 0);

                return new DateRangeResult(earliest, latest, total, null);
            } else {
                return new DateRangeResult(null, null, 0, "Server Error (" + statusCode + "): " + responseString);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new DateRangeResult(null, null, 0, "Network Error: " + e.getMessage());
        }
    }

    public static FilterOptionsResult fetchFilterOptions(String serverUrl) {
        String optionsUrl = serverUrl + "/api/audit_logs/actions";

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet optionsRequest = new HttpGet(optionsUrl);
            ClassicHttpResponse response = httpClient.execute(optionsRequest);
            int statusCode = response.getCode();
            String responseString = EntityUtils.toString(response.getEntity());

            if (statusCode == 200) {
                JSONObject jsonResponse = new JSONObject(responseString);

                List<String> actions = new ArrayList<>();
                List<String> users = new ArrayList<>();

                if (jsonResponse.has("actions")) {
                    JSONArray actionsArray = jsonResponse.getJSONArray("actions");
                    for (int i = 0; i < actionsArray.length(); i++) {
                        actions.add(actionsArray.getString(i));
                    }
                }

                if (jsonResponse.has("users")) {
                    JSONArray usersArray = jsonResponse.getJSONArray("users");
                    for (int i = 0; i < usersArray.length(); i++) {
                        users.add(usersArray.getString(i));
                    }
                }

                return new FilterOptionsResult(actions, users, null);
            } else {
                return new FilterOptionsResult(null, null, "Server Error (" + statusCode + "): " + responseString);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new FilterOptionsResult(null, null, "Network Error: " + e.getMessage());
        }
    }
}