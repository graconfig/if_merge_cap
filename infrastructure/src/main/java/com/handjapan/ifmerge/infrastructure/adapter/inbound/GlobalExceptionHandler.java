package com.handjapan.ifmerge.infrastructure.adapter.inbound;

import com.handjapan.ifmerge.domain.shared.exception.AnalysisException;
import com.handjapan.ifmerge.domain.shared.exception.MergeException;
import com.handjapan.ifmerge.infrastructure.adapter.inbound.dto.ErrorResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * REST 入口の例外を統一的に {@link ErrorResponseDto} へマップする。
 *
 * <p>注意：解析/合并の業務処理は @Async（{@code runAsync}）の別スレッドで実行され、
 * 例外はそこで catch されて Job.error に格納される（HTTP には伝播しない）。
 * 従ってここで主に拾うのは、
 * <ul>
 *   <li>リクエストボディの JSON 解析失敗（400）</li>
 *   <li>Bean Validation 失敗（400）</li>
 *   <li>同期パスで投げられた業務例外 / 想定外例外（500）</li>
 * </ul>
 * これにより、各 Controller に散在していたエラー応答の形式が統一される。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 存在しないパス / 静的リソース → 404（catch-all で 500 に化けるのを防ぐ）。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseDto.of("NOT_FOUND", "リソースが見つかりません: " + e.getResourcePath()));
    }

    /** リクエストボディが壊れている / 解析不能 → 400。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("不正なリクエストボディ: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponseDto.of("BAD_REQUEST", "リクエストボディを解析できません"));
    }

    /** Bean Validation（@Valid）失敗 → 400。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("入力検証エラー");
        log.warn("入力検証エラー: {}", msg);
        return ResponseEntity.badRequest()
                .body(ErrorResponseDto.of("VALIDATION_ERROR", msg));
    }

    /** 不正な引数 → 400。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("不正な引数: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponseDto.of("BAD_REQUEST", e.getMessage()));
    }

    /** 業務例外（同期パスで発生した場合）→ 500。code は例外が持つ業務コード。 */
    @ExceptionHandler({AnalysisException.class, MergeException.class})
    public ResponseEntity<ErrorResponseDto> handleDomain(RuntimeException e) {
        String code = (e instanceof AnalysisException ae) ? ae.code()
                : ((MergeException) e).code();
        log.error("業務例外: code={}, msg={}", code, e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDto.of(code, e.getMessage()));
    }

    /** 想定外の例外 → 500（詳細はログのみ、応答には出さない）。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneric(Exception e) {
        log.error("想定外のエラー", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDto.of("INTERNAL_ERROR", "サーバー内部エラーが発生しました"));
    }
}
