package com.example.yazhang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class EnvironmentSmokeTest {

    @Test
    public void junitAndJsonAreReadyForDomainTests() throws Exception {
        JSONObject value = new JSONObject()
                .put("name", "芽账")
                .put("local", true);

        assertEquals("芽账", value.getString("name"));
        assertTrue(value.getBoolean("local"));
    }
}
