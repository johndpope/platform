package io.fundrequest.platform.tweb.request;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.fundrequest.common.infrastructure.AbstractControllerTest;
import io.fundrequest.common.infrastructure.mapping.Mappers;
import io.fundrequest.core.infrastructure.SecurityContextService;
import io.fundrequest.core.platform.PlatformIssueDto;
import io.fundrequest.core.platform.PlatformIssueService;
import io.fundrequest.core.request.RequestService;
import io.fundrequest.core.request.claim.ClaimService;
import io.fundrequest.core.request.claim.dto.ClaimsByTransactionAggregate;
import io.fundrequest.core.request.claim.dto.UserClaimableDto;
import io.fundrequest.core.request.domain.Platform;
import io.fundrequest.core.request.fiat.FiatService;
import io.fundrequest.core.request.fund.FundService;
import io.fundrequest.core.request.fund.PendingFundService;
import io.fundrequest.core.request.fund.RefundService;
import io.fundrequest.core.request.fund.dto.CommentDto;
import io.fundrequest.core.request.fund.dto.FundsForRequestDto;
import io.fundrequest.core.request.fund.dto.PendingFundDto;
import io.fundrequest.core.request.fund.dto.RefundRequestDto;
import io.fundrequest.core.request.statistics.StatisticsService;
import io.fundrequest.core.request.statistics.dto.StatisticsDto;
import io.fundrequest.core.request.view.AllFundsDto;
import io.fundrequest.core.request.view.IssueInformationDto;
import io.fundrequest.core.request.view.RequestDto;
import io.fundrequest.core.request.view.RequestDtoMother;
import io.fundrequest.core.token.dto.TokenValueDto;
import io.fundrequest.core.token.dto.TokenValueDtoMother;
import io.fundrequest.platform.profile.profile.ProfileService;
import io.fundrequest.platform.tweb.request.dto.RequestDetailsView;
import io.fundrequest.platform.tweb.request.dto.RequestView;
import org.assertj.core.util.Lists;
import org.junit.Test;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.fundrequest.core.request.domain.RequestPhase.OPEN;
import static io.fundrequest.core.request.fund.domain.RefundRequestStatus.APPROVED;
import static io.fundrequest.core.request.fund.domain.RefundRequestStatus.PENDING;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

public class RequestControllerTest extends AbstractControllerTest<RequestController> {

    private Principal principal;
    private SecurityContextService securityContextService;
    private RequestService requestService;
    private PendingFundService pendingFundService;
    private StatisticsService statisticsService;
    private ProfileService profileService;
    private FundService fundService;
    private RefundService refundService;
    private ClaimService claimService;
    private FiatService fiatService;
    private ObjectMapper objectMapper;
    private Mappers mappers;
    private PlatformIssueService platformIssueService;

    @Override
    protected RequestController setupController() {
        principal = mock(Principal.class);
        when(principal.getName()).thenReturn("davyvanroy@fundrequest.io");
        securityContextService = mock(SecurityContextService.class);
        requestService = mock(RequestService.class);
        pendingFundService = mock(PendingFundService.class);
        statisticsService = mock(StatisticsService.class);
        profileService = mock(ProfileService.class, RETURNS_DEEP_STUBS);
        fundService = mock(FundService.class);
        refundService = mock(RefundService.class);
        claimService = mock(ClaimService.class);
        fiatService = mock(FiatService.class);
        platformIssueService = mock(PlatformIssueService.class);
        objectMapper = spy(new ObjectMapper());
        mappers = mock(Mappers.class);
        return new RequestController(securityContextService,
                                     requestService,
                                     pendingFundService,
                                     statisticsService,
                                     profileService,
                                     fundService,
                                     refundService,
                                     claimService,
                                     fiatService,
                                     platformIssueService,
                                     objectMapper,
                                     mappers);
    }

    @Test
    public void requests() throws Exception {
        final List<RequestDto> requestDtos = new ArrayList<>();
        final List<RequestView> returnedRequestViews = Arrays.asList(buildRequestView("2", null),
                                                                     buildRequestView("0", null),
                                                                     buildRequestView(null, "3"),
                                                                     buildRequestView(null, "0"),
                                                                     buildRequestView("5", "4"),
                                                                     buildRequestView("0", "0"),
                                                                     buildRequestView(null, null));
        final List<RequestView> filteredRequestViews = Arrays.asList(buildRequestView("2", null),
                                                                     buildRequestView(null, "3"),
                                                                     buildRequestView("5", "4"));
        final StatisticsDto statisticsDto = StatisticsDto.builder().build();
        final Set<String> projects = new HashSet<>();
        final Set<String> technologies = new HashSet<>();
        boolean isAuthenticated = false;

        when(requestService.findAll()).thenReturn(requestDtos);
        when(mappers.mapList(RequestDto.class, RequestView.class, requestDtos)).thenReturn(returnedRequestViews);
        when(statisticsService.getStatistics()).thenReturn(statisticsDto);
        when(requestService.findAllProjects()).thenReturn(projects);
        when(requestService.findAllTechnologies()).thenReturn(technologies);
        when(securityContextService.isUserFullyAuthenticated()).thenReturn(isAuthenticated);
        when(objectMapper.writeValueAsString(filteredRequestViews)).thenReturn("requestViews");
        when(objectMapper.writeValueAsString(same(projects))).thenReturn("projects");
        when(objectMapper.writeValueAsString(same(technologies))).thenReturn("technologies");

        this.mockMvc.perform(get("/requests").principal(principal))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.model().attribute("requests", "requestViews"))
                    .andExpect(MockMvcResultMatchers.model().attribute("statistics", statisticsDto))
                    .andExpect(MockMvcResultMatchers.model().attribute("projects", "projects"))
                    .andExpect(MockMvcResultMatchers.model().attribute("technologies", "technologies"))
                    .andExpect(MockMvcResultMatchers.model().attribute("isAuthenticated", Boolean.toString(isAuthenticated)))
                    .andExpect(MockMvcResultMatchers.view().name("pages/requests/index"));
    }

    private RequestView buildRequestView(final String fndAmount, final String zrxAmount) {
        return RequestView.builder()
                          .phase(OPEN.name())
                          .funds(AllFundsDto.builder()
                                            .fndFunds(fndAmount != null ? TokenValueDtoMother.FND().totalAmount(new BigDecimal(fndAmount)).build() : null)
                                            .otherFunds(zrxAmount != null ? TokenValueDtoMother.ZRX().totalAmount(new BigDecimal(zrxAmount)).build() : null).build()).build();
    }

    @Test
    public void details() throws Exception {
        final long requestId = 7458L;
        final RequestDto requestDto = mock(RequestDto.class);
        final RequestDetailsView requestDetailsView = mock(RequestDetailsView.class);
        final FundsForRequestDto fundsForRequestDto = mock(FundsForRequestDto.class);
        final List<RefundRequestDto> pendingRefundRequests = Lists.newArrayList(RefundRequestDto.builder().funderAddress("0xGDjhg4354").build(),
                                                                                RefundRequestDto.builder().funderAddress("0xFEFSkjhkhj5436").build());
        final List<String> expectedPendingRefundAddresses = Lists.newArrayList("0xgdjhg4354", "0xfefskjhkhj5436");
        final ClaimsByTransactionAggregate claims = mock(ClaimsByTransactionAggregate.class);
        final List<CommentDto> commentDtos = new ArrayList<>();

        when(requestService.findRequest(requestId)).thenReturn(requestDto);
        when(mappers.map(RequestDto.class, RequestDetailsView.class, requestDto)).thenReturn(requestDetailsView);
        when(objectMapper.writeValueAsString(same(requestDetailsView))).thenReturn("requestDetailsView");
        when(fundService.getFundsForRequestGroupedByFunder(requestId)).thenReturn(fundsForRequestDto);
        when(refundService.findAllRefundRequestsFor(requestId, PENDING, APPROVED)).thenReturn(pendingRefundRequests);
        when(claimService.getAggregatedClaimsForRequest(requestId)).thenReturn(claims);
        when(requestService.getComments(requestId)).thenReturn(commentDtos);

        this.mockMvc.perform(get("/requests/{id}", requestId).principal(principal))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.model().attribute("request", requestDetailsView))
                    .andExpect(MockMvcResultMatchers.model().attribute("requestJson", "requestDetailsView"))
                    .andExpect(MockMvcResultMatchers.model().attribute("funds", fundsForRequestDto))
                    .andExpect(MockMvcResultMatchers.model().attribute("pendingRefundAddresses", expectedPendingRefundAddresses))
                    .andExpect(MockMvcResultMatchers.model().attribute("claims", claims))
                    .andExpect(MockMvcResultMatchers.model().attribute("githubComments", sameInstance(commentDtos)))
                    .andExpect(MockMvcResultMatchers.view().name("pages/requests/detail"));
    }

    @Test
    public void githubDetails() throws Exception {
        final String owner = "blablaOwner";
        final String repo = "blablaRepo";
        final int number = 351;
        final long requestId = 7458L;
        final RequestDto requestDto = mock(RequestDto.class);
        final RequestDetailsView requestDetailsView = mock(RequestDetailsView.class);
        final FundsForRequestDto fundsForRequestDto = mock(FundsForRequestDto.class);
        final List<CommentDto> commentDtos = new ArrayList<>();

        when(requestService.findRequest(Platform.GITHUB, owner + "|FR|" + repo + "|FR|" + number)).thenReturn(requestDto);
        when(requestDetailsView.getId()).thenReturn(requestId);
        when(mappers.map(eq(RequestDto.class), eq(RequestDetailsView.class), same(requestDto))).thenReturn(requestDetailsView);
        when(objectMapper.writeValueAsString(same(requestDetailsView))).thenReturn("requestDetailsView");
        when(fundService.getFundsForRequestGroupedByFunder(requestId)).thenReturn(fundsForRequestDto);
        when(requestService.getComments(requestId)).thenReturn(commentDtos);

        this.mockMvc.perform(get("/requests/github/{owner}/{repo}/{number}", owner, repo, number).principal(principal))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.model().attribute("request", requestDetailsView))
                    .andExpect(MockMvcResultMatchers.model().attribute("requestJson", "requestDetailsView"))
                    .andExpect(MockMvcResultMatchers.model().attribute("funds", fundsForRequestDto))
                    .andExpect(MockMvcResultMatchers.model().attribute("githubComments", sameInstance(commentDtos)))
                    .andExpect(MockMvcResultMatchers.view().name("pages/requests/detail"));
    }

    @Test
    public void detailsBadge_otherFund_HighestFiat() throws Exception {
        final long requestId = 654L;
        final RequestDto request = RequestDtoMother.freeCodeCampNoUserStories();
        final TokenValueDto fndFunds = TokenValueDto.builder()
                                                    .tokenSymbol("FND")
                                                    .totalAmount(new BigDecimal("1000"))
                                                    .build();
        final TokenValueDto otherFunds = TokenValueDto.builder()
                                                      .tokenSymbol("SDFGG")
                                                      .totalAmount(new BigDecimal("1100"))
                                                      .build();
        request.getFunds().setFndFunds(fndFunds);
        request.getFunds().setOtherFunds(otherFunds);

        when(requestService.findRequest(requestId)).thenReturn(request);
        when(fiatService.getUsdPrice(request.getFunds().getFndFunds())).thenReturn(100D);
        when(fiatService.getUsdPrice(request.getFunds().getOtherFunds())).thenReturn(110D);

        this.mockMvc.perform(get("/requests/{id}/badge", 654L).principal(principal))
                    .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue()))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.model().attribute("requestPhase", request.getStatus().getPhase()))
                    .andExpect(MockMvcResultMatchers.model().attribute("highestFunds", otherFunds))
                    .andExpect(MockMvcResultMatchers.view().name("requests/badge.svg"));
    }

    @Test
    public void detailsBadge_FND_HighestFiat() throws Exception {
        final long requestId = 654L;
        final RequestDto request = RequestDtoMother.freeCodeCampNoUserStories();
        final TokenValueDto fndFunds = TokenValueDto.builder()
                                                    .tokenSymbol("FND")
                                                    .totalAmount(new BigDecimal("1000"))
                                                    .build();
        final TokenValueDto otherFunds = TokenValueDto.builder()
                                                      .tokenSymbol("SDFGG")
                                                      .totalAmount(new BigDecimal("1100"))
                                                      .build();
        request.getFunds().setFndFunds(fndFunds);
        request.getFunds().setOtherFunds(otherFunds);

        when(requestService.findRequest(requestId)).thenReturn(request);
        when(fiatService.getUsdPrice(request.getFunds().getFndFunds())).thenReturn(120D);
        when(fiatService.getUsdPrice(request.getFunds().getOtherFunds())).thenReturn(100D);

        this.mockMvc.perform(get("/requests/{id}/badge", 654L).principal(principal))
                    .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue()))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.model().attribute("requestPhase", request.getStatus().getPhase()))
                    .andExpect(MockMvcResultMatchers.model().attribute("highestFunds", fndFunds))
                    .andExpect(MockMvcResultMatchers.view().name("requests/badge.svg"));
    }

    @Test
    public void detailActions() throws Exception {
        final RequestDto request = RequestDtoMother.freeCodeCampNoUserStories();
        final IssueInformationDto issueInformation = request.getIssueInformation();
        final UserClaimableDto userClaimableDto = mock(UserClaimableDto.class);
        final PlatformIssueDto platformIssue = mock(PlatformIssueDto.class);

        when(requestService.findRequest(1L)).thenReturn(request);
        when(requestService.getUserClaimableResult(principal, 1L)).thenReturn(userClaimableDto);
        when(platformIssueService.findBy(issueInformation.getPlatform(), issueInformation.getPlatformId())).thenReturn(Optional.of(platformIssue));

        this.mockMvc.perform(get("/requests/{id}/actions", 1L).principal(principal))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.view().name("pages/requests/detail-actions :: details"))
                    .andExpect(MockMvcResultMatchers.model().attribute("request", sameInstance(request)))
                    .andExpect(MockMvcResultMatchers.model().attribute("userClaimable", sameInstance(userClaimableDto)))
                    .andExpect(MockMvcResultMatchers.model().attribute("platformIssue", sameInstance(platformIssue)));
    }

    @Test
    public void claimRequest() throws Exception {
        RequestDto request = RequestDtoMother.freeCodeCampNoUserStories();
        when(requestService.findRequest(1L)).thenReturn(request);
        UserClaimableDto userClaimableDto = UserClaimableDto.builder().build();
        when(requestService.getUserClaimableResult(principal, 1L)).thenReturn(userClaimableDto);
        when(profileService.getUserProfile(principal.getName()).getEtherAddress()).thenReturn("0x0000000");

        this.mockMvc.perform(post("/requests/{id}/claim", 1L).principal(principal))
                    .andExpect(redirectAlert("success", "Your claim has been requested and is waiting for approval."))
                    .andExpect(redirectedUrl("/requests/" + 1L));
    }

    @Test
    public void claimRequestShowsErrorMsg() throws Exception {
        when(profileService.getUserProfile(principal.getName()).getEtherAddress()).thenReturn("");

        this.mockMvc.perform(post("/requests/{id}/claim", 1L).principal(principal))
                    .andExpect(redirectAlert("danger", "Please update <a href=\"/profile\">your profile</a> with a correct ether address."))
                    .andExpect(redirectedUrl("/requests/" + 1L));
    }

    @Test
    public void toggleWatchRequest() throws Exception {
        RequestDto request = RequestDtoMother.freeCodeCampNoUserStories();
        RequestView requestView = new RequestView();
        when(requestService.findRequest(1L)).thenReturn(request);
        when(mappers.map(RequestDto.class, RequestView.class, request)).thenReturn(requestView);
        when(objectMapper.writeValueAsString(requestView)).thenReturn("{ starred: true }");

        this.mockMvc.perform(post("/requests/{id}/watch", 1L).principal(principal))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.content().json("{ starred: true }"));
    }

    @Test
    public void userRequests() throws Exception {
        final List<RequestDto> requests = new ArrayList<>();
        final List<RequestView> requestViews = new ArrayList<>();
        final List<PendingFundDto> pendingFunds = new ArrayList<>();
        final Set<String> projects = new HashSet<>();
        boolean isAuthenticated = false;

        when(requestService.findRequestsForUser(principal)).thenReturn(requests);
        when(mappers.mapList(RequestDto.class, RequestView.class, requests)).thenReturn(requestViews);
        when(pendingFundService.findByUser(principal)).thenReturn(pendingFunds);
        when(requestService.findAllProjects()).thenReturn(projects);
        when(securityContextService.isUserFullyAuthenticated()).thenReturn(isAuthenticated);
        when(objectMapper.writeValueAsString(same(projects))).thenReturn("projects");
        when(objectMapper.writeValueAsString(same(requestViews))).thenReturn("requestViews");
        when(objectMapper.writeValueAsString(same(pendingFunds))).thenReturn("pendingFunds");

        this.mockMvc.perform(get("/user/requests").principal(principal))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.model().attribute("requests", "requestViews"))
                    .andExpect(MockMvcResultMatchers.model().attribute("projects", "projects"))
                    .andExpect(MockMvcResultMatchers.model().attribute("pendingFunds", "pendingFunds"))
                    .andExpect(MockMvcResultMatchers.model().attribute("isAuthenticated", Boolean.toString(isAuthenticated)))
                    .andExpect(MockMvcResultMatchers.view().name("pages/user/requests"));
    }
}
