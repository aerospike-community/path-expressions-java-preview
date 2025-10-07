# Aerospike demo project

Overview

Aerospike’s Path Expressions capability introduces granular querying and indexing for nested List/Map structures using contextual expressions. This directly addresses common pain points when working with JSON-like models in databases.



With Path Expressions, developers can:

Select multiple nested elements in a single operation.

Traverse maps and lists using expressive filters (e.g., select only featured products with variants in stock).

Retrieve only the relevant subtrees from large documents, reducing network traffic and latency.

Use metadata variables (key, value, index) for flexible filtering.

Modify nested values directly on the server.

This makes it easier to use Aerospike as a document store and simplifies the developer experience for anyone working with hierarchical data.

 

Let’s Begin!

We’ll use a simplified e-commerce product catalog stored as a single record in Aerospike. Products are organized under a single inventory bin, keyed by product IDs (e.g., 10000001, 50000009).

Each product record includes:

category: product category (e.g., clothing, electronics)

featured: a boolean flag for merchandising

name and description: product metadata

variants: either a map of SKUs to attributes (size/spec, price, quantity) or a list of variant objects.

 

This structure mirrors real-world product catalogs, where a product may have multiple variants (sizes, colors, configurations), each with its own availability.

For this demo, let’s assume we want to identify products to promote on the home page of an ecommerce website but we only want to select products that are featured and have an quantity above 0.



Step 0. Download requisite server and client builds

[insert how to use applicable server and client versions that support Path Expressions - @Mirza Karacic can you help fill this out?]

Pull docker image of server that supports Path Expressions:

docker pull artifact.aerospike.io/database-container-preview-public-local/aerospike-server-enterprise:8.1.1.0-start-48

Run docker image:

docker run -it --rm -p 3000:3000 -p 3001:3001 -p 3002:3002 artifact.aerospike.io/database-container-preview-public-local/aerospike-server-enterprise:8.1.1.0-start-48

You can verify server is running properly by running asadm

asadm
...
Admin> info

To use the Aerospike Java Client preview release, add the Aerospike Preview Repository to your build tool configuration and include the desired dependency.

Step 1. Add the Aerospike Preview Repository

Maven

  <repositories>
  ...
    <repository>
      <id>aerospike-preview-public</id>
      <url>https://aerospike.jfrog.io/artifactory/clients-maven-preview-public-local</url>
    </repository>
  </repositories>

Gradle (Groovy DSL)

repositories {
    mavenCentral()
    maven {
        url "https://aerospike.jfrog.io/artifactory/clients-maven-preview-public-local"
    }
}

**Gradle (Kotlin DSL)

repositories {
    mavenCentral()
    maven("https://aerospike.jfrog.io/artifactory/clients-maven-preview-public-local")
}

Step 2. Add the Aerospike Java Client Dependency

The preview release version is 9.3.0-alpha.2. 

For below examples you only need to give one of the dependencies not all flavors of the artifact.

Maven

<!-- Java 21 GNU crypto -->
<dependency>
    <groupId>com.aerospike</groupId>
    <artifactId>aerospike-client-jdk21</artifactId>
    <version>9.3.0-alpha.2</version>
</dependency>

<!-- Java 21 Bouncycastle crypto -->
<dependency>
    <groupId>com.aerospike</groupId>
    <artifactId>aerospike-client-bc-jdk21</artifactId>
    <version>9.3.0-alpha.2</version>
</dependency>

<!-- Java 8 GNU crypto -->
<dependency>
    <groupId>com.aerospike</groupId>
    <artifactId>aerospike-client-jdk8</artifactId>
    <version>9.3.0-alpha.2</version>
</dependency>

<!-- Java 8 Bouncycastle crypto -->
<dependency>
    <groupId>com.aerospike</groupId>
    <artifactId>aerospike-client-bc-jdk8</artifactId>
    <version>9.3.0-alpha.2</version>
</dependency>

Gradle (Groovy DSL)

dependencies {
    // Java 21 GNU crypto
    implementation "com.aerospike:aerospike-client-jdk21:9.3.0-alpha.2"

    // Java 21 Bouncycastle crypto
    implementation "com.aerospike:aerospike-client-bc-jdk21:9.3.0-alpha.2"

    // Java 8 GNU crypto
    implementation "com.aerospike:aerospike-client-jdk8:9.3.0-alpha.2"

    // Java 8 Bouncycastle crypto
    implementation "com.aerospike:aerospike-client-bc-jdk8:9.3.0-alpha.2"
  }

Gradle (Kotlin DSL)

dependencies {
    // Java 21 GNU crypto
    implementation("com.aerospike:aerospike-client-jdk21:9.3.0-alpha.2")

    // Java 21 Bouncycastle crypto
    implementation("com.aerospike:aerospike-client-bc-jdk21:9.3.0-alpha.2")

    // Java 8 GNU crypto
    implementation("com.aerospike:aerospike-client-jdk8:9.3.0-alpha.2")

    // Java 8 Bouncycastle crypto
    implementation("com.aerospike:aerospike-client-bc-jdk8:9.3.0-alpha.2")
}

Step 1. Observe our nested dataset

Here is a look at the dataset:

{
  "inventory": {
    "10000001": {
      "category": "clothing",
      "featured": true,
      "name": "Classic T-Shirt",
      "description": "A lightweight cotton T-shirt perfect for everyday wear.",
      "variants": {
        "2001": { "size": "S", "price": 25, "quantity": 100 },
        "2002": { "size": "M", "price": 25, "quantity": 0 },
        "2003": { "size": "L", "price": 27, "quantity": 50 }
      }
    },
    "10000002": {
      "category": "clothing",
      "featured": false,
      "name": "Casual Polo Shirt",
      "description": "A soft polo shirt suitable for work or leisure.",
      "variants": {
        "2004": { "size": "M", "price": 30, "quantity": 20 },
        "2005": { "size": "XL", "price": 32, "quantity": 10 }
      }
    },
    "50000006": {
      "category": "electronics",
      "featured": true,
      "name": "Laptop Pro 14",
      "description": "High-performance laptop designed for professionals.",
      "variants": {
        "3001": { "spec": "8GB RAM", "price": 599, "quantity": 0 }
      }
    },
    "50000009": {
      "category": "electronics",
      "featured": true,
      "name": "Smart TV",
      "description": "Ultra HD smart television with built-in streaming apps.",
      "variants": [
        { "sku": 3007, "spec": "1080p", "price": 199, "quantity": 60 }, 
        { "sku": 3008, "spec": "4K", "price": 399, "quantity": 30 }
      ]
    }
  }
}



Step 2. Define filters as expressions

We’ll filter at two levels of the data:

Product-level: only include products where featured = true.

Variant-level: only include variants where quantity > 0.

Context stack: how filters apply

When traversing with Path Expressions, the server evaluates filters at different depths. Here’s the stack we’re walking for this example:

inventory (bin)
  └── product (map entry, keyed by productId)
        ├── category
        ├── featured   <-- product-level filter
        ├── name
        ├── description
        └── variants
              ├── { "2001": {...}, "2002": {...} }   <-- map-backed variants
              └── [ {sku:3007,...}, {sku:3008,...} ] <-- list-backed variants
                        ^ variant-level filter

At product level, we filter by the featured field inside each product map.

At variant level, we filter by the quantity field inside either:

a map-backed variant (keyed by SKU), or

a list-backed variant (array of objects).



// Product-level filter: featured == true
Exp filterOnFeatured = Exp.eq(
   MapExp.getByKey(
       MapReturnType.VALUE, Type.BOOL,
       Exp.val("featured"),
       Exp.mapLoopVar(LoopVarPart.VALUE)  // loop variable points to each product map
   ),
   Exp.val(true)
);

// Variant-level filter: quantity > 0
Exp filterOnVariantInventory = Exp.gt(
    MapExp.getByKey(
        MapReturnType.VALUE, Type.INT,
        Exp.val("quantity"),
        Exp.mapLoopVar(LoopVarPart.VALUE)  // loop variable points to each variant object
    ),
    Exp.val(0)
);




Step 3. Run Path Expression

Now we combine the filters with traversal contexts:

// Operation
Record record = client.operate(null, key,
    CDTOperation.cdtSelect("inventory", SelectFlags.MATCHING_TREE,
        CTX.allChildren(),                                   // dive into variants
        CTX.allChildrenWithFilter(filterOnFeatured),         // only featured products
        CTX.allChildrenWithFilter(filterOnVariantInventory)  // only in-stock variants
    )
);



Step 4. Observe the result

Print the returned dataset and see the server only returns products with featured = true and at least one variant in stock:

Java

System.out.println(record.getMap("inventory"));



Expected output:

 {inventory={10000001={variants={2001={size=S, price=25, quantity=100}, 2003={size=L, price=27, quantity=50}}}, 50000009={variants=[{quantity=60, sku=3007, price=199, spec=1080p}, {quantity=30, sku=3008, price=399, spec=4K}]}, 50000006={variants={}}}}

✅ item 50000009 keeps both variants.

✅ item 10000001, Classic T-Shirt, keeps  variant items 2001 and 2003 (both have quantity > 0).

❌ 10000002, Casual Polo Shirt,  excluded (featured = false).

❌  Variant 2002 excluded from 10000001(quantity = 0).

That’s it

With Path Expressions you can:

Traverse and filter nested Maps and Lists

Retrieve only relevant subtrees (products + in-stock variants)

Avoid denormalization and client-side filtering

Build faster, cleaner APIs for real-world use cases like product catalogs



Advanced Usage

The example above covers the most common use case: filtering and selecting products and their in-stock variants. Path Expressions can do more. This section highlights advanced capabilities you may want to use in your own applications.



1. Use LoopVar metadata (MAP_KEY, LIST_INDEX)

Loop variables let you filter by map keys or list indexes, not just values.

Example: Select only products whose key starts with SKU "1000".

Exp filterOnKey = 
    Exp.regexCompare("10000.*", 0, Exp.loopVarString(LoopVarPart.MAP_KEY));


// Operation
Record record = client.operate(null, key,
    CDTOperation.cdtSelect(binName, SelectFlags.MATCHING_TREE.flag,
        CTX.allChildren(),
        CTX.allChildrenWithFilter(filterOnKey)
    )
);

System.out.println(record.getMap(binName));



Expected output:

 {inventory={10000001={name=Classic T-Shirt, description=A lightweight cotton T-shirt perfect for everyday wear., featured=true, variants={2001={size=S, price=25, quantity=100}, 2003={size=L, price=27, quantity=50}, 2002={size=M, price=25, quantity=0}}, category=clothing}, 10000002={name=Casual Polo Shirt, description=A soft polo shirt suitable for work or leisure., featured=false, variants={2005={size=XL, price=32, quantity=10}, 2004={size=M, price=30, quantity=20}}, category=clothing}}}

✅ Only products whose keys start with "10000" are included.



2. Alternate return modes with SelectFlags

Sometimes you don’t want the full tree but just the keys or values.

Example: Return only the SKUs of in-stock variants for featured products that follow a map/dictionary structure.

Exp filterOnKey = 
    Exp.regexCompare("10000.*", 0, Exp.loopVarString(LoopVarPart.MAP_KEY));


// Operation
Record record = client.operate(null, key,
    CDTOperation.cdtSelect(binName, SelectFlags.MAP_KEYS.flag,
        CTX.allChildren(),
        CTX.allChildrenWithFilter(filterOnKey)
    )
);

System.out.println(record.getList(binName));



Expected Output:

[10000001, 10000002]

✅ Only the keys from SKU’s 10000001 and 10000001are returned.

⚠️ Item 50000009`, Smart TV, has list-backed variants, so no map keys to return.



3. Modify nested elements with modifyCdt

You can update selected values in place.

Example: Increase quantity by +10 for all in-stock variants of featured products.

String updatedBin = "updatedBinName";

// Increment quantity by 10
Exp incrementExp = Exp.add(
    MapExp.getByKey(MapReturnType.VALUE, Type.INT,
        Exp.val("quantity"),
        Exp.loopVarMap(LoopVarPart.VALUE)
    ),
    Exp.val(10)
);

Expression modifyExpression = Exp.build(
    CDTExp.cdtModify(
        Exp.Type.MAP,
        SelectFlags.MATCHING_TREE.flag,
        incrementExp,
        Exp.mapBin(binName),
        CTX.allChildren(),
        CTX.allChildrenWithFilter(filterOnFeatured),
        CTX.mapKey(Value.get("variants")),
        CTX.allChildrenWithFilter(filterOnVariantInventory)
    )
);

// Write the modified map to a new bin
client.operate(null, key, 
    ExpOperation.write(updatedBin, modifyExpression, ExpWriteFlags.DEFAULT)
);

// Read back the updated record
Record updatedRecord = client.get(null, key);
System.out.println("Updated records: " + updatedRecord.getMap(updatedBin));



Expected output (diff excerpt):

{inventory={10000001={name=Classic T-Shirt, description=A lightweight cotton T-shirt perfect for everyday wear., featured=true, variants={2001=110, 2003=60, 2002={size=M, price=25, quantity=0}}, category=clothing}, 50000009={name=Smart TV, description=Ultra HD smart television with built-in streaming apps., featured=true, variants=[70, 40], category=electronics}, 10000002={name=Casual Polo Shirt, description=A soft polo shirt suitable for work or leisure., featured=false, variants={2005={size=XL, price=32, quantity=10}, 2004={size=M, price=30, quantity=20}}, category=clothing}, 50000006={name=Laptop Pro 14, description=High-performance laptop designed for professionals., featured=true, variants={3001={price=599, spec=8GB RAM, quantity=0}}, category=electronics}}}

✅ Inventories for in-stock variants are incremented directly on the server.



4. Combine multiple filters

Filters can be chained with AND / OR.

Example: Select variants that are in stock and have price < 50.

Expression filterOnCheapInStock = Exp.build(Exp.and(
    Exp.gt(
        MapExp.getByKey(MapReturnType.VALUE, Type.INT,
            Exp.val("quantity"),
            Exp.mapLoopVar(LoopVarPart.VALUE)
        ),
        Exp.val(0)
    ),
    Exp.lt(
        MapExp.getByKey(MapReturnType.VALUE, Type.INT,
            Exp.val("price"),
            Exp.mapLoopVar(LoopVarPart.VALUE)
        ),
        Exp.val(50)
    )
));

Record record = client.operate(null, key,
    Operation.pathExpression("inventory", SelectFlags.MATCHING_TREE,
        CTX.allChildrenWithFilter(filterOnFeatured),
        CTX.allChildren(),
        CTX.allChildrenWithFilter(filterOnCheapInStock)
    )
);



Expected Output:

{
  "inventory": {
    "10000001": {
      "category": "clothing",
      "featured": true,
      "name": "Classic T-Shirt",
      "description": "A lightweight cotton T-shirt perfect for everyday wear.",
      "variants": {
        "2001": { "size": "S", "price": 25, "quantity": 100 }, 
        "2003": { "size": "L", "price": 27, "quantity": 50}
      }
    }
  }
}

✅ Only items are returned which have price < 50 and  quantity > 0

❌ Item 10000002, Casual Polo Shirt, excluded (featured=false).

❌ Item 50000006, Laptop Pro 14, excluded (quantity=0).

❌ Item 50000009, Smart TV, both variants excluded (price > 50).



5) NO_FAIL: tolerate malformed product

Now let’s assume we add this item to the inventory bin:


"10000003": {
  "category": "clothing",
  "featured": true,
  "name": "Hooded Sweatshirt",
  "description": "Warm fleece hoodie with front pocket and adjustable hood.",
  "variants": "no variant"
}

Because the dataset now includes 10000003 with variants: "no variant" (a string), any traversal that reaches variants and then tries to treat it like a Map/List will hit a type mismatch and error unless SelectFlags.NO_FAIL is set.

Record noFailResponse = client.operate(null, key,
    CDTOperation.cdtSelect(binName, SelectFlags.MATCHING_TREE.flag | SelectFlags.NO_FAIL.flag,
        CTX.allChildren(),
        CTX.allChildrenWithFilter(filterOnFeatured),
        CTX.mapKey(Value.get("variants")),
        CTX.allChildrenWithFilter(filterOnVariantInventory)
    )
);

malformed_product.variants is "no variant".

With NO_FAIL, malformed_product excluded silently because variants was "no variant"



Expected output:

Same as the corresponding non-NO_FAIL query, minus any contribution from malformed_product:

{inventory={10000001={variants={2001={size=S, price=25, quantity=100}, 2003={size=L, price=27, quantity=50}}}, 50000009={variants=[{quantity=60, sku=3007, price=199, spec=1080p}, {quantity=30, sku=3008, price=399, spec=4K}]}, 50000006={variants={}}}, 10000003={}

✅ Item 10000003skipped silently because variants was a string.



FAQs



Q: What does the selectFlags parameter do, and what options are available?

A: The selectFlags parameter controls what the server returns from a path expression. Possible values are:

MATCHING_TREE,   // Return a tree from the root (bin) level to the bottom of the tree, with only non-filtered out nodes
VALUES,          // Return the list of the values of the nodes finally selected by the context
MAP_KEYS,        // For final selected nodes which are elements of maps, return the appropiate map key
MAP_KEY_VALUES,  // Return a list of Key-value pairs
NO_FAIL          // If the expression in the context hits an invalid type (eg selects as an integer when the value is a string), do not fail the operation, just ignore those elements.



Q: Can I use Path Expressions on both Maps and Lists?

A: Yes. CTX.allChildren and CTX.allChildrenWithFilter work across both Maps and Lists. Loop variables (MAP_KEY, VALUE, LIST_INDEX) allow filters to adapt depending on whether the container is a map of entries or a list of elements.



Q: What kind of exception will I actually see on the client if I don’t use NO_FAIL? Is it recoverable or will the whole operation abort?

A: Without SelectFlags.NO_FAIL, if the server encounters a type mismatch (e.g., it expects a Map or List but finds a String), the entire path expression operation fails. The operation does not return partial results.



Q: How do I return only certain pieces of data, like just the variant IDs?

A: The SelectFlags parameter controls return modes. For example, MATCHING_TREE returns the full subtree, MAP_KEYS returns only keys, and VALUES returns just the values. Choose the mode that matches your use case.



Q: Can Path Expressions be combined with secondary indexes?

A: Yes. Expression-based indexes can be created on filtered elements of Maps/Lists. This lets you query and traverse deeply nested structures without denormalizing data.



API Specifications



Operation.pathExpression(...)

Purpose: Read/select nested elements from Maps and Lists using contexts (CTX).

Inputs:

binName (String) — name of the CDT bin.

selectFlags (SelectFlags) — controls what is returned (MATCHING_TREE, VALUES, MAP_KEYS, MAP_KEY_VALUES, NO_FAIL).

CTX... — one or more contexts defining traversal and filters.



Returns: Operation (executed via client.operate to return a Record).

Operation.modifyCdt(...)

Purpose: Update nested elements in Maps or Lists directly on the server.

Inputs:

binName (String) — name of the CDT bin.

modifyingExpression (Exp) — expression applied to each selected element.

CTX... — one or more contexts defining traversal and filters.



Returns: Operation (executed via client.operate to return an updated Record).

Loop Variable Expressions

Exposes metadata of the current element (key, value, or index) during traversal.

Exp.mapLoopVar(LoopVarPart field)

Exp.listLoopVar(LoopVarPart field)

Exp.stringLoopVar(LoopVarPart field)

Exp.intLoopVar(LoopVarPart field)

Exp.floatLoopVar(LoopVarPart field)

Exp.boolLoopVar(LoopVarPart field)

Exp.blobLoopVar(LoopVarPart field)

Inputs:

field (LoopVarPart) — which part of the element to expose (MAP_KEY, VALUE, LIST_INDEX).

Returns:

An Exp object usable inside filter or modifying expressions.
