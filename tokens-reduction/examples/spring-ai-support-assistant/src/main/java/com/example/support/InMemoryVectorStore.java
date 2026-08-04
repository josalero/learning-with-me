package com.example.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

/**
 * A trivial in-memory {@link VectorStore} that returns synthetic chunks, so the example runs without
 * a real embedding model or database. In a production application this would be a pgvector, Redis, or
 * other Spring AI vector store backed by an embedding model.
 */
public class InMemoryVectorStore implements VectorStore {

	private final List<Document> documents = new ArrayList<>();

	@Override
	public void add(List<Document> documents) {
		this.documents.addAll(documents);
	}

	@Override
	public void delete(List<String> idList) {
		this.documents.removeIf(document -> idList.contains(document.getId()));
	}

	@Override
	public void delete(Filter.Expression filterExpression) {
		// The demo store does not support metadata-filtered deletes.
	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {
		return IntStream.range(0, request.getTopK())
				.mapToObj(index -> new Document(
						"doc-" + index,
						"Chunk " + index + " relevant to: " + request.getQuery(),
						Map.of()))
				.toList();
	}
}
