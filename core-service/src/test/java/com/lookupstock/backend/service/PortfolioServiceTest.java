package com.lookupstock.backend.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.never;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.lookupstock.backend.dto.PortfolioRequest;
import com.lookupstock.backend.dto.PositionRequest;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.Position;
import com.lookupstock.backend.model.UsageEventType;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.queue.PricePreloadRequester;
import com.lookupstock.backend.queue.UsageEventRecorder;
import com.lookupstock.backend.repository.AssetRepository;
import com.lookupstock.backend.repository.PortfolioRepository;

class PortfolioServiceTest {

    private static final User OWNER = new User("anna@example.com", "hash");

    private static final User OTHER = new User("ben@example.com", "hash");

    private PortfolioRepository portfolioRepository;
    private AssetRepository assetRepository;
    private UsageEventRecorder usageEventRecorder;
    private PricePreloadRequester pricePreloadRequester;

    private PortfolioService service;

    @BeforeEach
    void setUp() {
        portfolioRepository = mock(PortfolioRepository.class);
        assetRepository = mock(AssetRepository.class);
        usageEventRecorder = mock(UsageEventRecorder.class);
        pricePreloadRequester = mock(PricePreloadRequester.class);
        service = new PortfolioService(portfolioRepository, assetRepository, usageEventRecorder, pricePreloadRequester);

        when(portfolioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void findAll_returnsPortfoliosOfOwnerFromRepository() {
        Portfolio first = new Portfolio("Depot A", OWNER);
        Portfolio second = new Portfolio("Depot B", OWNER);
        when(portfolioRepository.findAllByOwnerOrderByCreatedAtDesc(OWNER)).thenReturn(List.of(first, second));

        List<Portfolio> result = service.findAll(OWNER);

        assertThat(result).containsExactly(first, second);
    }

    @Test
    void findById_existingId_returnsPortfolio() {
        Portfolio portfolio = new Portfolio("Depot A", OWNER);
        when(portfolioRepository.findByIdAndOwner(1L, OWNER)).thenReturn(Optional.of(portfolio));

        assertThat(service.findById(1L, OWNER)).isSameAs(portfolio);
    }

    @Test
    void findById_unknownId_throwsNotFound() {
        when(portfolioRepository.findByIdAndOwner(99L, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L, OWNER))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(Messages.PORTFOLIO_NOT_FOUND);
    }

    @Test
    void findById_portfolioEinesAnderenNutzers_liefert404_NF04() {
        when(portfolioRepository.findByIdAndOwner(1L, OTHER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(1L, OTHER))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(Messages.PORTFOLIO_NOT_FOUND);

        verify(portfolioRepository, never()).findById(any());
    }

    @Test
    void create_validPortfolio_savesAndReturnsIt() {
        Asset apple = asset(1L, "AAPL");
        Asset microsoft = asset(2L, "MSFT");
        when(assetRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(apple, microsoft));

        Portfolio result = service.create(request("Mein Depot",
                position(1L, "60"),
                position(2L, "40")), OWNER);

        ArgumentCaptor<Portfolio> captor = ArgumentCaptor.forClass(Portfolio.class);
        verify(portfolioRepository).save(captor.capture());
        Portfolio saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Mein Depot");
        assertThat(saved.getOwner()).isSameAs(OWNER);
        assertThat(saved.getPositions())
                .extracting(p -> p.getAsset().getSymbol())
                .containsExactly("AAPL", "MSFT");
        assertThat(saved.getPositions())
                .extracting(Position::getWeight)
                .containsExactly(new BigDecimal("60"), new BigDecimal("40"));

        assertThat(result).isSameAs(saved);
        verify(usageEventRecorder).record(UsageEventType.PORTFOLIO_CREATED);
        verify(pricePreloadRequester).request(saved);
    }

    @Test
    void create_unknownAssetId_throwsValidationAndDoesNotSave() {
        givenAssets();

        assertThatThrownBy(() -> service.create(request("Mein Depot", position(99L, "100")), OWNER))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(Messages.ASSET_NOT_FOUND);
                    assertThat(e.getErrors()).containsExactly(
                            new ValidationException.FieldError("positions", Messages.unknownAssetDetail(99L)));
                });

        verify(portfolioRepository, never()).save(any());
        verify(usageEventRecorder, never()).record(any());
        verify(pricePreloadRequester, never()).request(any());
    }

    @Test
    void create_missingAssetId_isRejected() {
        givenAssets();
        assertRejected(request("Mein Depot", position(null, "100")),
                "positions", Messages.unknownAssetDetail(null));
    }

    @Test
    void create_blankName_isRejected() {
        givenAssets(asset(1L, "AAPL"));
        assertRejected(request("   ", position(1L, "100")),
                "name", Messages.NAME_INVALID);
    }

    @Test
    void create_nameWith101Characters_isRejected() {
        givenAssets(asset(1L, "AAPL"));
        assertRejected(request("a".repeat(101), position(1L, "100")),
                "name", Messages.NAME_INVALID);
    }

    @Test
    void create_withoutPositions_isRejected() {
        assertRejected(new PortfolioRequest("Mein Depot", null),
                "positions", Messages.POSITIONS_EMPTY);
    }

    @Test
    void create_weightsNotSummingTo100_isRejected() {
        givenAssets(asset(1L, "AAPL"), asset(2L, "MSFT"));
        assertRejected(request("Mein Depot", position(1L, "60"), position(2L, "30")),
                "positions", Messages.weightSumDetail(new BigDecimal("90")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-10"})
    void create_weightZeroOrNegative_isRejected(String weight) {
        givenAssets(asset(1L, "AAPL"), asset(2L, "MSFT"));
        assertRejected(request("Mein Depot", position(1L, weight), position(2L, "100")),
                "positions", Messages.WEIGHT_NOT_POSITIVE);
    }

    @Test
    void create_weightWithThreeDecimals_isRejected() {
        givenAssets(asset(1L, "AAPL"), asset(2L, "MSFT"));
        assertRejected(request("Mein Depot", position(1L, "33.333"), position(2L, "66.667")),
                "positions", Messages.WEIGHT_TOO_MANY_DECIMALS);
    }

    @Test
    void create_sameAssetTwice_isRejected() {
        givenAssets(asset(1L, "AAPL"));
        assertRejected(request("Mein Depot", position(1L, "50"), position(1L, "50")),
                "positions", Messages.DUPLICATE_ASSET);
    }

    @Test
    void create_nameWithExactly100Characters_isAccepted() {
        givenAssets(asset(1L, "AAPL"));

        assertThatCode(() -> service.create(request("a".repeat(100), position(1L, "100")), OWNER))
                .doesNotThrowAnyException();
        verify(portfolioRepository).save(any());
    }

    @Test
    void create_sumOf99_99_isAcceptedWithinTolerance() {
        givenAssets(asset(1L, "AAPL"), asset(2L, "MSFT"));

        assertThatCode(() -> service.create(request("Mein Depot",
                position(1L, "49.99"), position(2L, "50")), OWNER))
                .doesNotThrowAnyException();
        verify(portfolioRepository).save(any());
    }

    @Test
    void create_sumOf99_98_isRejected() {
        givenAssets(asset(1L, "AAPL"), asset(2L, "MSFT"));

        assertRejected(request("Mein Depot", position(1L, "49.98"), position(2L, "50")),
                "positions", Messages.weightSumDetail(new BigDecimal("99.98")));
    }

    @Test
    void update_aendertNameGewichtungenUndAnlagen() {
        Asset apple = asset(1L, "AAPL");
        Asset microsoft = asset(2L, "MSFT");
        Asset nvidia = asset(3L, "NVDA");
        Portfolio existing = new Portfolio("Alt", OWNER);
        existing.addPosition(apple, new BigDecimal("50"));
        existing.addPosition(microsoft, new BigDecimal("50"));
        Position applePosition = existing.getPositions().get(0);
        when(portfolioRepository.findByIdAndOwner(1L, OWNER)).thenReturn(Optional.of(existing));
        givenAssets(apple, nvidia);

        Portfolio result = service.update(1L, request("Neu", position(1L, "70"), position(3L, "30")), OWNER);

        assertThat(result).isSameAs(existing);
        assertThat(result.getName()).isEqualTo("Neu");
        assertThat(result.getPositions()).extracting(p -> p.getAsset().getSymbol()).containsExactly("AAPL", "NVDA");
        assertThat(result.getPositions()).extracting(Position::getWeight)
                .containsExactly(new BigDecimal("70"), new BigDecimal("30"));
        assertThat(result.getPositions().get(0)).isSameAs(applePosition);
        assertThat(result.getUpdatedAt()).isNotNull();
        verify(portfolioRepository).save(existing);
        verify(pricePreloadRequester).request(existing);
    }

    @Test
    void update_ungueltigeGewichtung_laesstPortfolioUnveraendert() {
        Asset apple = asset(1L, "AAPL");
        Portfolio existing = new Portfolio("Alt", OWNER);
        existing.addPosition(apple, new BigDecimal("100"));
        when(portfolioRepository.findByIdAndOwner(1L, OWNER)).thenReturn(Optional.of(existing));
        givenAssets(apple);

        assertThatThrownBy(() -> service.update(1L, request("Neu", position(1L, "90")), OWNER))
                .isInstanceOf(ValidationException.class)
                .hasMessage(Messages.WEIGHT_SUM);

        assertThat(existing.getName()).isEqualTo("Alt");
        assertThat(existing.getPositions()).extracting(Position::getWeight).containsExactly(new BigDecimal("100"));
        verify(portfolioRepository, never()).save(any());
        verify(pricePreloadRequester, never()).request(any());
    }

    @Test
    void update_fremdesPortfolio_liefert404_NF04() {
        when(portfolioRepository.findByIdAndOwner(1L, OTHER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, request("X", position(1L, "100")), OTHER))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(Messages.PORTFOLIO_NOT_FOUND);
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    void delete_eigenesPortfolio_wirdGeloescht() {
        Portfolio existing = new Portfolio("Alt", OWNER);
        when(portfolioRepository.findByIdAndOwner(1L, OWNER)).thenReturn(Optional.of(existing));

        service.delete(1L, OWNER);

        verify(portfolioRepository).delete(existing);
    }

    @Test
    void delete_fremdesPortfolio_liefert404_NF04() {
        when(portfolioRepository.findByIdAndOwner(1L, OTHER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, OTHER))
                .isInstanceOf(NotFoundException.class);
        verify(portfolioRepository, never()).delete(any());
    }

    private static Asset asset(long id, String symbol) {
        Asset asset = new Asset(symbol, null, symbol + " Inc.", "NASDAQ", "USD", "Common Stock", "XNAS");
        ReflectionTestUtils.setField(asset, "id", id);
        return asset;
    }

    private static PositionRequest position(Long assetId, String weight) {
        return new PositionRequest(assetId, new BigDecimal(weight));
    }

    private static PortfolioRequest request(String name, PositionRequest... positions) {
        return new PortfolioRequest(name, List.of(positions));
    }

    private void givenAssets(Asset... assets) {
        when(assetRepository.findAllById(any())).thenReturn(List.of(assets));
    }

    private void assertRejected(PortfolioRequest request, String expectedField, String expectedMessage) {
        assertThatThrownBy(() -> service.create(request, OWNER))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.getErrors())
                                .contains(new ValidationException.FieldError(expectedField, expectedMessage)));
        verify(portfolioRepository, never()).save(any());
    }
}