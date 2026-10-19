package dev.mytechprofile.langgraph4j;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * LangGraph4j orchestrates a Cursor Agent (or local stub) behind a small REST UI.
 */
@SpringBootApplication
public class LangGraph4jApplication {

    public static void main(String[] args) {
        SpringApplication.run(LangGraph4jApplication.class, args);
    }
}
