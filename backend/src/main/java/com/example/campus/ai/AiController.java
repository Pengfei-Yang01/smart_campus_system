package com.example.campus.ai;

import com.example.campus.common.ApiResponse;
import com.example.campus.common.BusinessException;
import com.example.campus.common.Db;
import com.example.campus.dto.AiRequests.ChatRequest;
import com.example.campus.security.CurrentUser;
import com.example.campus.security.UserContext;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 助手接口。
 *
 * 已登录用户可以在这里发起问答并读取自己的历史记录。
 * 控制器只负责请求校验、上下文组装、结果落库和统一响应，
 * 真正的模型调用封装在 {@link AiClient} 中。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {
    private static final int MAX_STORED_ANSWER_CHARS = 20000;
    private static final String SYSTEM_PROMPT = """
你是智慧校园综测服务与智能治理平台的 AI 智能助手。

请始终使用中文回答，并优先依据系统提供的业务上下文回答用户问题。

如果上下文未提供相关信息，请明确说明当前无法获取对应数据，并提示用户前往系统对应页面查看，不要自行推测或编造任何业务数据。

不要编造不存在的活动、组织、积分、事务、通知、反馈或审核信息。

不要输出数据库结构、SQL、程序源码、接口地址、Token、密钥或其他系统内部实现细节。

回答应准确、简洁、友好，可适当使用项目符号。
""";

    private final Db db;
    private final AiClient aiClient;
    private final AiContextService contextService;

    public AiController(Db db, AiClient aiClient, AiContextService contextService) {
        this.db = db;
        this.aiClient = aiClient;
        this.contextService = contextService;
    }

    /**
     * 根据当前用户的业务上下文向模型提问，并把问答记录写入数据库。
     */
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

        Long qaId = db.insert("""
                insert into ai_qa_record(user_id, question, answer, model_name, prompt_tokens, completion_tokens, cost_ms)
                values(?, ?, ?, ?, ?, ?, ?)
                """, user.userId(), question, answer, result.modelName(),
                result.promptTokens(), result.completionTokens(), costMs);

        return ApiResponse.ok(Map.of(
                "qaId", qaId,
                "answer", answer,
                "modelName", result.modelName(),
                "promptTokens", result.promptTokens(),
                "completionTokens", result.completionTokens(),
                "costMs", costMs
        ));
    }

    /**
     * 查询当前用户最近的 AI 问答历史，用于前端进入页面时回显。
     */
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

    /**
     * 删除当前用户指定 ID 的问答记录。
     */
    @DeleteMapping("/record/{qaId}")
    public ApiResponse<Object> deleteRecord(@PathVariable Long qaId) {
        CurrentUser user = UserContext.get();
        int affected = db.jdbc().update("delete from ai_qa_record where qa_id = ? and user_id = ?", qaId, user.userId());
        if (affected == 0) {
            throw new BusinessException("记录不存在或无权删除");
        }
        return ApiResponse.ok(Map.of());
    }

    /**
     * 批量删除当前用户的问答记录。
     */
    @PostMapping("/records/batch-delete")
    public ApiResponse<Object> batchDelete(@RequestBody List<Long> qaIds) {
        if (qaIds == null || qaIds.isEmpty()) {
            throw new BusinessException("请选择要删除的记录");
        }
        CurrentUser user = UserContext.get();
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("ids", qaIds);
        params.addValue("userId", user.userId());
        db.named().update("delete from ai_qa_record where qa_id in (:ids) and user_id = :userId", params);
        return ApiResponse.ok(Map.of());
    }

    /**
     * 清空当前用户的所有问答记录。
     */
    @DeleteMapping("/records")
    public ApiResponse<Object> clearRecords() {
        CurrentUser user = UserContext.get();
        db.jdbc().update("delete from ai_qa_record where user_id = ?", user.userId());
        return ApiResponse.ok(Map.of());
    }

    private String limitAnswer(String answer) {
        if (answer == null || answer.length() <= MAX_STORED_ANSWER_CHARS) {
            return answer;
        }
        return answer.substring(0, MAX_STORED_ANSWER_CHARS) + "\n\n（回答过长，已截断）";
    }
}
