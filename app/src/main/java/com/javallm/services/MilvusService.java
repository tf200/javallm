package com.javallm.services;

import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.JsonObject;

import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.common.IndexParam.MetricType;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.GetLoadStateReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;

@Service
public class MilvusService {

        private static final Logger logger = LoggerFactory.getLogger(MilvusService.class);

        @Autowired
        private MilvusClientV2 milvusClient;
        // Default collection configuration
        private static final String DEFAULT_DATABASE_NAME = "micla"; // Adjust as needed
        private static final String DEFAULT_COLLECTION_NAME = "micla_embeddings";
        private static final int VECTOR_DIMENSION = 384; // Adjust based on your embedding model
        private static final String VECTOR_FIELD = "embedding";
        private static final String ID_FIELD = "id";
        private static final String FILE_ID = "file_id"; // Unique identifier for the file
        private static final String TEXT_FIELD = "text";
        private static final String DOCUMENT_NAME = "document_name";
        private static final String DOCUMENT_PAGES = "document_pages";

        public void initializeCollection() {
                initializeCollection(DEFAULT_COLLECTION_NAME, VECTOR_DIMENSION);
        }

        public void initializeCollection(String collectionName, int vectorDimension) {
                try {
                        HasCollectionReq hasCollectionReq = HasCollectionReq.builder()
                                        .collectionName(collectionName)
                                        .build();
                        boolean exists = milvusClient.hasCollection(hasCollectionReq);

                        if (exists) {
                                logger.info("Collection '{}' already exists.", collectionName);
                                return;
                        }
                        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                                        .build();

                        schema.addField(AddFieldReq.builder()
                                        .fieldName(ID_FIELD)
                                        .dataType(DataType.Int64)
                                        .isPrimaryKey(true)
                                        .autoID(true)
                                        .build());
                        schema.addField(AddFieldReq.builder()
                                        .fieldName(FILE_ID)
                                        .dataType(DataType.VarChar)
                                        .build());

                        schema.addField(AddFieldReq.builder()
                                        .fieldName(TEXT_FIELD)
                                        .dataType(DataType.VarChar)
                                        .build());
                        schema.addField(AddFieldReq.builder()
                                        .fieldName(DOCUMENT_NAME)
                                        .dataType(DataType.VarChar)
                                        .build());
                        schema.addField(AddFieldReq.builder()
                                        .fieldName(DOCUMENT_PAGES)
                                        .dataType(DataType.VarChar)
                                        .build());

                        schema.addField(AddFieldReq.builder()
                                        .fieldName(VECTOR_FIELD)
                                        .dataType(DataType.FloatVector)
                                        .dimension(vectorDimension)
                                        .build());

                        // Create indexes for the fields

                        IndexParam indexParamForIdField = IndexParam.builder()
                                        .fieldName(ID_FIELD)
                                        .indexType(IndexParam.IndexType.AUTOINDEX) // No index for ID field
                                        .build();
                        IndexParam indexParamForVectorField = IndexParam.builder()
                                        .fieldName(VECTOR_FIELD)
                                        .indexType(IndexParam.IndexType.AUTOINDEX)
                                        .metricType(MetricType.COSINE) // Adjust metric type as needed
                                        .build();
                        List<IndexParam> indexParams = Arrays.asList(indexParamForIdField, indexParamForVectorField);

                        CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                                        .collectionName(collectionName)
                                        .collectionSchema(schema)
                                        .indexParams(indexParams)
                                        .build();

                        milvusClient.createCollection(createCollectionReq);
                        logger.info("Collection '{}' created successfully with vector dimension {}.", collectionName,
                                        vectorDimension);

                } catch (Exception e) {
                        logger.error("Failed to initialize collection '{}': {}", collectionName, e.getMessage(), e);
                        throw new RuntimeException("Failed to initialize collection: " + e.getMessage(), e);

                }
        }

        public void insertPDFData(List<JsonObject> data) {
                try {
                        InsertReq insertReq = InsertReq.builder()
                                        .collectionName(DEFAULT_COLLECTION_NAME)
                                        .data(data)
                                        .build();

                        milvusClient.insert(insertReq);
                        logger.info("Successfully inserted {} records into collection '{}'.", data.size(),
                                        DEFAULT_COLLECTION_NAME);

                } catch (Exception e) {
                        logger.error("Failed to insert data into collection '{}': {}", DEFAULT_COLLECTION_NAME,
                                        e.getMessage(), e);
                        throw new RuntimeException("Failed to insert data: " + e.getMessage(), e);
                }

        }

        public List<QueryResult> queryCollection(FloatVec queryVector) {
                // Load the collection if it is not loaded
                GetLoadStateReq loadStateReq = GetLoadStateReq.builder()
                                .collectionName(DEFAULT_COLLECTION_NAME)
                                .build();
                boolean isLoaded = milvusClient.getLoadState(loadStateReq);
                if (!isLoaded) {
                        logger.info("Collection '{}' is not loaded, loading it now.", DEFAULT_COLLECTION_NAME);
                        LoadCollectionReq loadCollectionReq = LoadCollectionReq.builder()
                                        .collectionName(DEFAULT_COLLECTION_NAME)
                                        .build();
                        milvusClient.loadCollection(loadCollectionReq);
                } else {
                        logger.info("Collection '{}' is already loaded.", DEFAULT_COLLECTION_NAME);
                }
                // build the search request, asking Milvus to return our 3 scalar fields
                SearchReq searchReq = SearchReq.builder()
                                .databaseName(DEFAULT_DATABASE_NAME)
                                .collectionName(DEFAULT_COLLECTION_NAME)
                                .annsField(VECTOR_FIELD)
                                .data(Collections.singletonList(queryVector))
                                .topK(7)
                                .metricType(MetricType.COSINE)
                                .outputFields(Arrays.asList(DOCUMENT_NAME, DOCUMENT_PAGES, TEXT_FIELD, FILE_ID))
                                .build();
                // :contentReference[oaicite:0]{index=0}
                // execute the search
                SearchResp searchResp = milvusClient.search(searchReq);
                logger.info("Search completed with {} results.", searchResp.getSearchResults().size());

                List<QueryResult> results = new ArrayList<>();
                // Milvus returns List<List<SearchResult>>, one inner list per query vector
                for (List<SearchResp.SearchResult> hits : searchResp.getSearchResults()) {
                        for (SearchResp.SearchResult hit : hits) {
                                // getEntity() is a wrapper around the scalar fields map
                                Map<String, ?> entity = hit.getEntity();
                                String name = (String) entity.get(DOCUMENT_NAME);
                                String pages = ((String) entity.get(DOCUMENT_PAGES));
                                String text = (String) entity.get(TEXT_FIELD);
                                String fileId = (String) entity.get(FILE_ID);
                                float score = hit.getScore();

                                results.add(new QueryResult(name, pages, text, score, fileId));
                        }
                }
                logger.info("Retrieved {} results from the search.", results.size());
                return results;
        }

        public void deleteEmbeddingsByFileId(String fileId) {
                try {
                        DeleteReq deleteReq = DeleteReq.builder()
                                        .collectionName(DEFAULT_COLLECTION_NAME)
                                        .filter("file_id == '" + fileId + "'")
                                        .build();
                        milvusClient.delete(deleteReq);
                        logger.info("Successfully deleted embeddings for file ID '{}'.", fileId);
                } catch (Exception e) {
                        logger.error("Failed to delete embeddings for file ID '{}': {}", fileId, e.getMessage(), e);
                        throw new RuntimeException("Failed to delete embeddings: " + e.getMessage(), e);
                }
        }

        public class QueryResult {
                private final String documentName;
                private final String documentPages;
                private final String text;
                private final float score;
                private final String fileId;

                public QueryResult(String documentName, String documentPages, String text, float score, String fileId) {
                        this.documentName = documentName;
                        this.documentPages = documentPages;
                        this.text = text;
                        this.score = score;
                        this.fileId = fileId;
                }

                public String getDocumentName() {
                        return documentName;
                }

                public String getDocumentPages() {
                        return documentPages;
                }

                public String getText() {
                        return text;
                }

                public float getScore() {
                        return score;
                }

                public String getFileId() {
                        return fileId;
                }
        }
}