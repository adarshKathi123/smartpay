package com.smartpay.controller;

import com.smartpay.dto.PageResponse;
import com.smartpay.dto.TransactionHistoryItem;
import com.smartpay.service.TransactionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Transactions", description = "Transaction history (transfers and deposits).")
public class TransactionController {

    private final TransactionHistoryService historyService;

    public TransactionController(TransactionHistoryService historyService) {
        this.historyService = historyService;
    }

    // GET /api/transactions?page=0&size=20  (page starts at 0, size is capped at 50)
    @Operation(summary = "List my transactions, newest first (paginated)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of transactions"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token")
    })
    @GetMapping
    public PageResponse<TransactionHistoryItem> history(
            Authentication authentication,
            @Parameter(description = "Page number, starting at 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1 to 50, default 20)")
            @RequestParam(defaultValue = "20") int size) {
        Long userId = Long.valueOf(authentication.getName());
        return historyService.getHistory(userId, page, size);
    }
}