package com.aerospike.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Debug {
    static ObjectMapper mapper = new ObjectMapper();

    public static String print(Object inventoryData) {
        String jsonOutput = "";

        if (inventoryData != null) {
            try {
                jsonOutput = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(inventoryData);
            } catch (JsonProcessingException e) {
            }
        } 

        return jsonOutput;
    }
}
