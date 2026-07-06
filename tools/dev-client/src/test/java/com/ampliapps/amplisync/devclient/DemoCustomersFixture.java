package com.ampliapps.amplisync.devclient;

import java.util.Map;

public class DemoCustomersFixture {
    static final String TABLE = "demo_customers";

    static final String UPDATED_CUSTOMER_ID = "0b8e9b8e-0fb5-4f2d-8d4c-3c57e7dc8e47";
    static final String DELETED_CUSTOMER_ID = "8fb5f9c7-9929-4f87-8fcb-19f2092f0a5d";

    static Map<String, Object> insertedCustomer(String id) {
        return Map.of(
                "id", id,
                "name", "Inserted From Device A",
                "email", "inserted-device-a@example.com",
                "city", "Warsaw"
        );
    }

    static Map<String, Object> updatedCustomerValues() {
        return Map.of(
                "city", "Wroclaw"
        );
    }

    static Map<String, Object> expectedUpdatedCustomer() {
        return Map.of(
                "id", UPDATED_CUSTOMER_ID,
                "name", "North Coast Shop",
                "email", "hello@northcoast.example",
                "city", "Wroclaw"
        );
    }

}

