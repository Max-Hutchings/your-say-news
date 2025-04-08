package com.yoursay.model;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.util.HashMap;
import java.util.Map;

@MongoEntity(collection="post_characteristic_totals")
public class VoteCharacteristicTotal extends PanacheMongoEntity {

    private Long id;
    private Long postId;
    private final Map<String, Integer> characteristicFields = new HashMap<>();


    public void addCharacteristic(String field) {
        characteristicFields.compute(field, (k, v) -> (v == null) ? 1 : v + 1);
    }
}
