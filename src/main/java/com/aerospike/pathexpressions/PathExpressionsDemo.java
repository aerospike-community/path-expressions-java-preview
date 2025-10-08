package com.aerospike.pathexpressions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Host;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.Value;
import com.aerospike.client.cdt.CDTOperation;
import com.aerospike.client.cdt.CTX;
import com.aerospike.client.cdt.MapReturnType;
import com.aerospike.client.exp.CDTExp;
import com.aerospike.client.exp.Exp;
import com.aerospike.client.exp.Exp.Type;
import com.aerospike.client.exp.ExpOperation;
import com.aerospike.client.exp.ExpWriteFlags;
import com.aerospike.client.exp.Expression;
import com.aerospike.client.exp.LoopVarPart;
import com.aerospike.client.exp.MapExp;
import com.aerospike.client.operation.SelectFlags;
import com.aerospike.client.policy.ClientPolicy;
import com.aerospike.client.policy.RecordExistsAction;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.util.Debug;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PathExpressionsDemo {

@SuppressWarnings("resource")
public static void main(String[] args) throws IOException {
    String json = new String(Files.readAllBytes(Paths.get("./data/inventory_sample.json")));

    ObjectMapper mapper = new ObjectMapper();

    // Parse once
    JsonNode root = mapper.readTree(json);

    Map<String, Object> inventory = mapper.convertValue(root, new TypeReference<Map<String, Object>>() {
    });

    ClientPolicy clientPolicy = new ClientPolicy();
    clientPolicy.useServicesAlternate = true;

    // Initialize client
    AerospikeClient client = new AerospikeClient(clientPolicy, new Host("localhost", 3000));
    // Truncate the inventory set
    client.truncate(null, "test", "products", null);
    String binName = "inventory";
    // insert the inventory
    Key key = new Key("test", "products", "catalog");
    client.put(null, key, new Bin(binName, inventory));
    System.out.println("Inventory inserted");

    // Read back the data and print in JSON format
    Record retrievedRecord = client.get(null, key);
    if (retrievedRecord != null) {
      Object inventoryData = retrievedRecord.getValue(binName);
      String jsonOutput = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(inventoryData);
      System.out.println("\n" + "=".repeat(80));
      System.out.println("STEP 1: Retrieved inventory data");
      System.out.println("=".repeat(80));
      System.out.println(jsonOutput);
    } else {
        throw new RuntimeException("No record found");
    }

    // Product-level filter: featured == true
    Exp filterOnFeatured = Exp.eq(
        MapExp.getByKey(
            MapReturnType.VALUE, Exp.Type.BOOL,
            Exp.val("featured"),
            Exp.loopVarMap(LoopVarPart.VALUE) // loop variable points to each product map
        ),
        Exp.val(true));

    // Variant-level filter: quantity > 0
    Exp filterOnVariantInventory = Exp.gt(
        MapExp.getByKey(
            MapReturnType.VALUE, Exp.Type.INT,
            Exp.val("quantity"),
            Exp.loopVarMap(LoopVarPart.VALUE)),
        Exp.val(0));

    // Operation
    Record readResult = client.operate(null, key,
        CDTOperation.selectByPath(binName, SelectFlags.MATCHING_TREE.flag,
            CTX.allChildren(),
            CTX.allChildrenWithFilter(filterOnFeatured),
            CTX.mapKey(Value.get("variants")),
            CTX.allChildrenWithFilter(filterOnVariantInventory)));

    System.out.println("\n" + "=".repeat(80));
    System.out.println("MAIN EXAMPLE (Steps 2-4): Featured products with variants having inventory > 0");
    System.out.println("=".repeat(80));
    System.out.println(Debug.print(readResult.getMap(binName)));

    // Fetch all the keys that start with 10000.*
    System.out.println("\n" + "=".repeat(80));
    System.out.println("ADVANCED EXAMPLE 1: Using LoopVar with regex filter on map keys");
    System.out.println("=".repeat(80));
    Exp filterOnKey = Exp.regexCompare("10000.*", 0, Exp.loopVarString(LoopVarPart.MAP_KEY));

    // Operation
    Record regexMatchingTree = client.operate(null, key,
        CDTOperation.selectByPath(binName, SelectFlags.MATCHING_TREE.flag,
            CTX.allChildren(),
            CTX.allChildrenWithFilter(filterOnKey)));

    System.out.println("Result (MATCHING_TREE): " + Debug.print(regexMatchingTree.getMap(binName)));

    // Operation
    System.out.println("\n" + "=".repeat(80));
    System.out.println("ADVANCED EXAMPLE 2: Alternate return modes with SelectFlags");
    System.out.println("=".repeat(80));
    Record regexList = client.operate(null, key,
        CDTOperation.selectByPath(binName, SelectFlags.MAP_KEYS.flag,
            CTX.allChildren(),
            CTX.allChildrenWithFilter(filterOnKey)));

    System.out.println("Result (MAP_KEYS only): " + Debug.print(regexList.getList(binName)));

    // Combine multiple filters
    System.out.println("\n" + "=".repeat(80));
    System.out.println("ADVANCED EXAMPLE 3: Combining multiple filters (price < 50 AND quantity > 0)");
    System.out.println("=".repeat(80));
    Exp filterOnCheapInStock = Exp.and(
        Exp.gt(
            MapExp.getByKey(MapReturnType.VALUE, Exp.Type.INT,
                Exp.val("quantity"),
                Exp.loopVarMap(LoopVarPart.VALUE)),
            Exp.val(0)),
        Exp.lt(
            MapExp.getByKey(MapReturnType.VALUE, Exp.Type.INT,
                Exp.val("price"),
                Exp.loopVarMap(LoopVarPart.VALUE)),
            Exp.val(50)));

    Record cheapInStock = client.operate(null, key,
        CDTOperation.selectByPath(binName, SelectFlags.MATCHING_TREE.flag,
            CTX.allChildren(),
            CTX.allChildren(),
            CTX.mapKey(Value.get("variants")),
            CTX.allChildrenWithFilter(filterOnCheapInStock)));

    System.out.println("Cheap in-stock items (across all products): " + Debug.print(cheapInStock.getMap(binName)));

    // Modify
    System.out.println("\n" + "=".repeat(80));
    System.out.println("ADVANCED EXAMPLE 4: Server-side modification (incrementing quantities)");
    System.out.println("=".repeat(80));
    String updatedBin = "updatedBinName";

    // Increment quantity by 10
    Exp incrementExp = Exp.add(
        MapExp.getByKey(MapReturnType.VALUE, Type.INT,
            Exp.val("quantity"),
            Exp.loopVarMap(LoopVarPart.VALUE)),
        Exp.val(10));

    Expression modifyExpression = Exp.build(
        CDTExp.modifyByPath(
            Exp.Type.MAP,
            SelectFlags.MATCHING_TREE.flag,
            incrementExp,
            Exp.mapBin(binName),
            CTX.allChildren(),
            CTX.allChildrenWithFilter(filterOnFeatured),
            CTX.mapKey(Value.get("variants")),
            CTX.allChildrenWithFilter(filterOnVariantInventory)));

    // Write the modified map to a new bin
    client.operate(null, key,
        ExpOperation.write(updatedBin, modifyExpression, ExpWriteFlags.DEFAULT));

    // Read back the updated record
    Record updatedRecord = client.get(null, key);
    System.out.println("Updated records (quantities incremented by 10): " + Debug.print(updatedRecord.getMap(updatedBin)));

    // NO_FAIL
    System.out.println("\n" + "=".repeat(80));
    System.out.println("ADVANCED EXAMPLE 5: NO_FAIL flag to tolerate malformed data");
    System.out.println("=".repeat(80));
    // Append bad record
    Map<String, Object> badRecordDetails = new HashMap<>();
    badRecordDetails.put("name", "Bad Product");
    badRecordDetails.put("category", "clothing");
    badRecordDetails.put("featured", true);
    badRecordDetails.put("name", "Hooded Sweatshirt");
    badRecordDetails.put("description", "Warm fleece hoodie with front pocket and adjustable hood.");
    badRecordDetails.put("variants", "no variant");

    inventory.put("10000003", badRecordDetails);
    WritePolicy writePolicy = new WritePolicy();
    writePolicy.recordExistsAction = RecordExistsAction.UPDATE_ONLY;
    client.put(null, key, new Bin(binName, inventory));

    try {
      client.operate(null, key,
          CDTOperation.selectByPath(binName, SelectFlags.MATCHING_TREE.flag,
              CTX.allChildren(),
              CTX.allChildrenWithFilter(filterOnFeatured),
              CTX.mapKey(Value.get("variants")),
              CTX.allChildrenWithFilter(filterOnVariantInventory)));
    } catch (AerospikeException e) {
      System.out.println("❌ Operation failed as expected (malformed data without NO_FAIL flag)");
    }

    Record noFailResponse = client.operate(null, key,
        CDTOperation.selectByPath(binName, SelectFlags.MATCHING_TREE.flag | SelectFlags.NO_FAIL.flag,
            CTX.allChildren(),
            CTX.allChildrenWithFilter(filterOnFeatured),
            CTX.mapKey(Value.get("variants")),
            CTX.allChildrenWithFilter(filterOnVariantInventory)));

    System.out.println("✅ Operation succeeded with NO_FAIL flag:");
    System.out.println(Debug.print(noFailResponse.getValue(binName)));

    client.close();
  }
}
