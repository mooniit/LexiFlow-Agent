package com.example.lexiflow.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lexiflow.rag.model.DocumentChunk;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    @Select("SELECT chunk_id, document_id, document_title, content, chunk_index, similarity, chunk_metadata "
            + "FROM search_chunks_by_similarity(#{embedding}::VECTOR, #{threshold}, #{limit}, "
            + "#{knowledgeBaseId}, #{documentType}, #{allowedRoles}::jsonb)")
    List<VectorSearchRow> searchByVector(@Param("embedding") String embedding,
                                          @Param("threshold") double threshold,
                                          @Param("limit") int limit,
                                          @Param("knowledgeBaseId") Long knowledgeBaseId,
                                          @Param("documentType") String documentType,
                                          @Param("allowedRoles") String allowedRoles);

    record VectorSearchRow(Long chunkId, Long documentId, String documentTitle, String content,
                           Integer chunkIndex, Double similarity, String chunkMetadata) {
    }
}
