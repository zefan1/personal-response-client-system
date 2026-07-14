package com.privateflow.modules.match.service;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.match.Confidence;
import com.privateflow.modules.match.CustomerMatchService;
import com.privateflow.modules.match.CustomerSummary;
import com.privateflow.modules.match.MatchRequest;
import com.privateflow.modules.match.MatchResult;
import com.privateflow.modules.match.MatchType;
import com.privateflow.modules.match.config.MatchConfigProvider;
import com.privateflow.modules.match.util.PhoneUtils;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MatchOrchestrator implements CustomerMatchService {

  private final ExactMatcher exactMatcher;
  private final FuzzyMatcher fuzzyMatcher;
  private final NicknamePrefixRemovalProcessor nicknamePrefixRemovalProcessor;
  private final ConfidenceEvaluator confidenceEvaluator;
  private final CandidateRanker candidateRanker;
  private final CustomerSummaryMapper summaryMapper;
  private final MatchConfigProvider configProvider;

  public MatchOrchestrator(
      ExactMatcher exactMatcher,
      FuzzyMatcher fuzzyMatcher,
      NicknamePrefixRemovalProcessor nicknamePrefixRemovalProcessor,
      ConfidenceEvaluator confidenceEvaluator,
      CandidateRanker candidateRanker,
      CustomerSummaryMapper summaryMapper,
      MatchConfigProvider configProvider) {
    this.exactMatcher = exactMatcher;
    this.fuzzyMatcher = fuzzyMatcher;
    this.nicknamePrefixRemovalProcessor = nicknamePrefixRemovalProcessor;
    this.confidenceEvaluator = confidenceEvaluator;
    this.candidateRanker = candidateRanker;
    this.summaryMapper = summaryMapper;
    this.configProvider = configProvider;
  }

  @Override
  public MatchResult match(MatchRequest request) {
    if (request == null) {
      return MatchResult.none();
    }
    String phone = PhoneUtils.clean(request.phone());
    String nickname = request.nickname() == null ? "" : request.nickname().trim();
    if (PhoneUtils.isValid(phone)) {
      Customer exact = exactMatcher.matchByPhone(phone);
      if (exact != null) {
        return new MatchResult(MatchType.EXACT, List.of(summaryMapper.toSummary(exact, null)), 1);
      }
    }
    if (nickname.isBlank()) {
      return MatchResult.none();
    }
    String cleanedNickname = nicknamePrefixRemovalProcessor.clean(nickname);
    if (cleanedNickname.isBlank()) {
      return MatchResult.none();
    }
    List<Customer> fuzzyCustomers = fuzzyMatcher.matchByNickname(cleanedNickname);
    if (fuzzyCustomers.isEmpty()) {
      return MatchResult.none();
    }
    List<CustomerSummary> candidates = fuzzyCustomers.stream()
        .map(customer -> summaryMapper.toSummary(
            customer,
            confidenceEvaluator.evaluate(cleanedNickname, customer.getNickname())))
        .toList();
    if (candidates.size() == 1 && candidates.get(0).confidence() == Confidence.HIGH) {
      return new MatchResult(MatchType.FUZZY, candidates, 1);
    }
    List<CustomerSummary> ranked = candidateRanker.rank(
        candidates,
        request.currentUser(),
        configProvider.get().maxCandidates());
    return new MatchResult(MatchType.MULTIPLE, ranked, candidates.size());
  }
}
