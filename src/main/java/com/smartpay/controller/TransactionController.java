package com.smartpay.controller;

import com.smartpay.dto.PageResponse;
import com.smartpay.dto.TransactionHistoryItem;
import com.smartpay.service.TransactionHistoryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionHistoryService historyService;

    public TransactionController(TransactionHistoryService historyService) {
        this.historyService = historyService;
    }

    // GET /api/transactions?page=0&size=20  (page starts at 0, size is capped at 50)
    @GetMapping
    public PageResponse<TransactionHistoryItem> history(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = Long.valueOf(authentication.getName());
        return historyService.getHistory(userId, page, size);
    }
}