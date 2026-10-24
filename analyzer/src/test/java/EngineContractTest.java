package com.example.codeanalyzer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EngineContractTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Test void graphJsonRoundtrip()throws Exception{
  String source="class Main {int f(int n){if(n>0)return n;return -n;}}";
  var result=mvc.perform(post("/engine/parse").contentType("application/json").content(json.writeValueAsString(Map.of("source",source))))
    .andExpect(status().isOk()).andExpect(jsonPath("$.schemaVersion").value("1.0")).andReturn();
  mvc.perform(post("/engine/generate").contentType("application/json").content(result.getResponse().getContentAsString()))
    .andExpect(status().isOk()).andExpect(jsonPath("$.source").value(source));
 }
 @Test void rejectsSyntax()throws Exception{
  mvc.perform(post("/engine/parse").contentType("application/json").content("{\"source\":\"class {\"}"))
   .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").exists());
 }
 @Test void realAnalysisHasComplexityAndNodeLink()throws Exception{
  String source="class Main {void f(int n){for(int i=0;i<n;i++){for(int j=0;j<n;j++){System.out.println(i+j);}}}}";
  mvc.perform(post("/engine/analyze").contentType("application/json").content(json.writeValueAsString(Map.of("source",source))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.methodsAnalyzed").value(1))
   .andExpect(jsonPath("$.issues[?(@.type == 'NESTED_LOOP')].nodeId").isNotEmpty());
 }
 @Test void java21AnalyzedOnHttpThread()throws Exception{
  String source="record Example(int n) {int f(){return switch(n){case 1 -> 2; default -> 0;};}}";
  mvc.perform(post("/engine/analyze").contentType("application/json").content(json.writeValueAsString(Map.of("source",source))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.issues[?(@.type == 'PARSE_ERROR')]").isEmpty());
 }
}
