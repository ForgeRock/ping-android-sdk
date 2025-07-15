package com.example;

import org.junit.Test;
import static org.junit.Assert.*;

public class NameCallbackTest {

    @Test
    public void testSetAndGetName() {
        NameCallback callback = new NameCallback("Enter your name:");
        callback.setName("Alice");
        assertEquals("Alice", callback.getName());
    }

    @Test
    public void testPrompt() {
        NameCallback callback = new NameCallback("Enter your name:");
        assertEquals("Enter your name:", callback.getPrompt());
    }
}
