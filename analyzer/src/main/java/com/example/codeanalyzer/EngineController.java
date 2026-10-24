package com.example.codeanalyzer;

import dev.mirutex.core.BlueprintEngine;
import dev.codex.refactor.AstRefactoring;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import java.util.*;

@RestController
@RequestMapping("/engine")
public class EngineController {
    private final BlueprintEngine core=new BlueprintEngine();
    private final AnalysisController analyzer;
    public EngineController(AnalysisController analyzer) {
        this.analyzer=analyzer;
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    }
    public record Source(String source) {}
    public record Edit(BlueprintEngine.Graph graph,BlueprintEngine.Operation operation) {}
    @GetMapping("/health") public Map<String,String> health(){return Map.of("status","ok");}
    @PostMapping("/parse") public BlueprintEngine.Graph parse(@RequestBody Source req){return core.parse(req.source());}
    @PostMapping("/generate") public BlueprintEngine.Graph generate(@RequestBody BlueprintEngine.Graph req){return core.generate(req);}
    @PostMapping("/edit") public BlueprintEngine.Graph edit(@RequestBody Edit req){return core.operate(req.graph(),req.operation());}
    @PostMapping("/refactor") public AstRefactoring.Preview refactor(@RequestBody Source req){return new AstRefactoring().preview(req.source());}
    @PostMapping("/analyze") public Map<String,Object> analyze(@RequestBody Source req){
        BlueprintEngine.Graph graph=core.parse(req.source());
        var r=new AnalysisController.AnalysisRequest();r.setCode(req.source());
        AnalysisReport report=(AnalysisReport)analyzer.analyze(r).getBody();
        List<Map<String,Object>> issues=new ArrayList<>();
        for(AnalysisIssue issue:report.getIssues()) {
            Map<String,Object> v=new LinkedHashMap<>();
            v.put("type",issue.getType());v.put("severity",issue.getSeverity());v.put("method",issue.getMethod());v.put("line",issue.getLine());
            v.put("complexity",issue.getComplexity());v.put("message",issue.getMessage());v.put("recommendation",issue.getRecommendation());
            String node=graph.nodes().stream().filter(n->n.line()<=issue.getLine()&&n.endLine()>=issue.getLine()&&!n.kind().equals("end"))
                .min(Comparator.comparingInt(n->n.endLine()-n.line())).map(BlueprintEngine.GraphNode::id).orElse("");
            v.put("nodeId",node);issues.add(v);
        }
        return Map.of("issues",issues,"methodsAnalyzed",report.getMethodsAnalyzed(),"summary",report.getSummary(),
            "note","Эвристический анализ. Стоимость библиотечных вызовов и динамическая диспетчеризация не доказываются.");
    }
    @ExceptionHandler({IllegalArgumentException.class,NoSuchElementException.class})
    public ResponseEntity<?> invalid(RuntimeException e){return ResponseEntity.unprocessableEntity().body(Map.of("detail",e.getMessage()==null?"Некорректный запрос":e.getMessage()));}
}
