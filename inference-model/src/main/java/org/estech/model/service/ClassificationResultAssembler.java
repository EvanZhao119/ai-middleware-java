package org.estech.model.service;

import ai.djl.modality.Classifications;
import org.estech.common.dto.ClassificationResult;

import java.util.LinkedHashMap;
import java.util.Map;

public class ClassificationResultAssembler {

    public static ClassificationResult from(Classifications classifications, int topK) {
        int safeTopK = Math.min(topK, classifications.items().size());

        Map<String, Double> topMap = new LinkedHashMap<>();
        classifications.topK(safeTopK).forEach(c -> topMap.put(c.getClassName(), c.getProbability()));

        return new ClassificationResult(
                classifications.best().getClassName(),
                classifications.best().getProbability(),
                topMap
        );
    }
}
