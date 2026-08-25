package com.aicommerce.platform.dashboard.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.ai.application.ReviewDecisionService;
import com.aicommerce.platform.ai.domain.GenerationOutput;
import com.aicommerce.platform.ai.infrastructure.persistence.GenerationOutputJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("(local | test) & !production")
@ConditionalOnExpression("'${platform.adapter:}' == 'fake' && '${platform.web.enabled:false}' == 'true' && '${platform.stage5.enabled:false}' == 'true'")
public class DashboardService {
    private final DashboardQueries queries;
    private final ReviewDecisionService reviews;
    private final GenerationOutputJpaRepository outputs;
    private final Clock clock;
    private final boolean stage4b;
    private final boolean stage4d;

    public DashboardService(DashboardQueries queries, ReviewDecisionService reviews,
            GenerationOutputJpaRepository outputs, Clock clock,
            @Value("${platform.stage4b.enabled:false}") boolean stage4b,
            @Value("${platform.stage4d.enabled:false}") boolean stage4d) {
        this.queries = queries;
        this.reviews = reviews;
        this.outputs = outputs;
        this.clock = clock;
        this.stage4b = stage4b;
        this.stage4d = stage4d;
    }

    @Transactional(readOnly = true)
    public DashboardViews.DashboardView summary() {
        UUID account = platformAccount();
        return new DashboardViews.DashboardView(
                DashboardViews.instant(clock.instant()),
                section(queries.todos(stage4b, 0, 20), true),
                section(queries.products(0, 20), true),
                reviewSection(queries.reviews(0, 20), true),
                section(queries.campaigns(0, 20), true),
                stage4b ? section(queries.platformCampaigns(account, 0, 20), true)
                        : new DashboardViews.DashboardSection<>(false, List.of(), false, 0),
                section(queries.anomalies(stage4b, 0, 20), true),
                stage4b && stage4d ? queries.kpis(account) : DashboardViews.KpiOverview.unavailable());
    }

    @Transactional(readOnly = true)
    public DashboardViews.DashboardPageView<?> page(String section, int page, int size) {
        int offset = page * size;
        return switch (section) {
            case "todos" -> pageView(queries.todos(stage4b, offset, size), page, size);
            case "products" -> pageView(queries.products(offset, size), page, size);
            case "reviews" -> reviewPage(queries.reviews(offset, size), page, size);
            case "campaigns" -> pageView(queries.campaigns(offset, size), page, size);
            case "platform-campaigns" -> stage4b
                    ? pageView(queries.platformCampaigns(platformAccount(), offset, size), page, size)
                    : emptyPage(page, size);
            case "anomalies" -> pageView(queries.anomalies(stage4b, offset, size), page, size);
            default -> throw new DashboardException("DASHBOARD_REQUEST_INVALID",
                    org.springframework.http.HttpStatus.BAD_REQUEST, "section");
        };
    }

    private UUID platformAccount() {
        return stage4b ? queries.account() : null;
    }

    private <T> DashboardViews.DashboardSection<T> section(DashboardQueries.Slice<T> slice, boolean available) {
        boolean truncated = slice.total() > slice.items().size();
        return new DashboardViews.DashboardSection<>(available, slice.items(), truncated, slice.total());
    }

    private DashboardViews.DashboardSection<DashboardViews.PendingReviewItem> reviewSection(
            DashboardQueries.Slice<DashboardQueries.ReviewRow> slice, boolean available) {
        List<DashboardViews.PendingReviewItem> items = slice.items().stream().map(this::reviewItem).toList();
        boolean truncated = slice.total() > items.size();
        return new DashboardViews.DashboardSection<>(available, items, truncated, slice.total());
    }

    private <T> DashboardViews.DashboardPageView<T> pageView(DashboardQueries.Slice<T> slice, int page, int size) {
        int totalPages = slice.total() == 0 ? 0 : (int) Math.ceil(slice.total() / (double) size);
        return new DashboardViews.DashboardPageView<>(slice.items(), page, size, slice.total(), totalPages);
    }

    private DashboardViews.DashboardPageView<DashboardViews.PendingReviewItem> reviewPage(
            DashboardQueries.Slice<DashboardQueries.ReviewRow> slice, int page, int size) {
        List<DashboardViews.PendingReviewItem> items = slice.items().stream().map(this::reviewItem).toList();
        int totalPages = slice.total() == 0 ? 0 : (int) Math.ceil(slice.total() / (double) size);
        return new DashboardViews.DashboardPageView<>(items, page, size, slice.total(), totalPages);
    }

    private DashboardViews.DashboardPageView<Object> emptyPage(int page, int size) {
        return new DashboardViews.DashboardPageView<>(List.of(), page, size, 0, 0);
    }

    private DashboardViews.PendingReviewItem reviewItem(DashboardQueries.ReviewRow row) {
        GenerationOutput output = outputs.findById(row.outputUuid()).orElseThrow();
        List<String> blockers = reviews.details(output).blockers();
        return new DashboardViews.PendingReviewItem(row.outputUuid(), row.productUuid(), row.generationType(),
                row.reviewStatus(), row.version(), blockers.size(), !blockers.isEmpty(),
                "/products/" + row.productUuid() + "?tab=creative-factory");
    }
}
