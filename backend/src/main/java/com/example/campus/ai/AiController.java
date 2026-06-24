package com.example.campus.ai;

import com.example.campus.common.ApiResponse;
import com.example.campus.common.BusinessException;
import com.example.campus.common.Db;
import com.example.campus.dto.AiRequests.ChatRequest;
import com.example.campus.security.CurrentUser;
import com.example.campus.security.UserContext;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI assistant endpoints for authenticated users.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {
    private static final int MAX_STORED_ANSWER_CHARS = 20000;
    private static final String SYSTEM_PROMPT = """
            你是智慧校园综合服务与活动管理系统的 AI 助手。
            使用中文回答。优先基于提供的系统业务上下文回答。
            如果上下文不足，请明确说明需要到对应页面查看或补充信息。
            不要编造不存在的活动、报名、积分或审核状态。
            不要输出数据库结构、SQL、密钥、token 或内部实现细节。
            回答尽量简洁，可使用项目符号。
            """;

    private final Db db;
    private final AiClient aiClient;
    private final AiContextService contextService;

    public AiController(Db db, AiClient aiClient, AiContextService contextService) {
        this.db = db;
        this.aiClient = aiClient;
        this.contextService = contextService;
    }

    @PostMapping("/chat")
    public ApiResponse<Object> chat(@RequestBody ChatRequest request) {
        CurrentUser user = UserContext.get();
        String question = Db.require(request.question(), "question");
        if (question.length() > 1000) {
            throw new BusinessException("问题不能超过 1000 字");
        }

        String userPrompt = "【系统业务上下文】\n" + contextService.build(user) + "\n【用户问题】\n" + question;
        long start = System.currentTimeMillis();
        AiClient.AiResult result = aiClient.chat(SYSTEM_PROMPT, userPrompt);
        int costMs = Math.toIntExact(Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - start));
        String answer = limitAnswer(result.answer());

        db.insert("""
                insert into ai_qa_record(user_id, question, answer, model_name, prompt_tokens, completion_tokens, cost_ms)
                values(?, ?, ?, ?, ?, ?, ?)
                """, user.userId(), question, answer, result.modelName(),
                result.promptTokens(), result.completionTokens(), costMs);

        return ApiResponse.ok(Map.of(
                "answer", answer,
                "modelName", result.modelName(),
                "promptTokens", result.promptTokens(),
                "completionTokens", result.completionTokens(),
                "costMs", costMs
        ));
    }

    @GetMapping("/records")
    public ApiResponse<Object> records() {
        CurrentUser user = UserContext.get();
        return ApiResponse.ok(db.jdbc().queryForList("""
                select qa_id, question, answer, model_name, prompt_tokens, completion_tokens, cost_ms, called_at
                from ai_qa_record
                where user_id = ?
                order by called_at desc
                limit 20
                """, user.userId()));
    }

    private String limitAnswer(String answer) {
        if (answer == null || answer.length() <= MAX_STORED_ANSWER_CHARS) {
            return answer;
        }
        return answer.substring(0, MAX_STORED_ANSWER_CHARS) + "\n\n（回答过长，已截断）";
    }
}
