package com.graphaware.pizzeria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.graphaware.pizzeria.model.Pizza;
import com.graphaware.pizzeria.model.PizzeriaUser;
import com.graphaware.pizzeria.model.Purchase;
import com.graphaware.pizzeria.model.PurchaseState;
import com.graphaware.pizzeria.repository.PizzeriaUserRepository;
import com.graphaware.pizzeria.repository.PurchaseRepository;
import com.graphaware.pizzeria.security.PizzeriaUserPrincipal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class PurchaseServiceTest {

    @Mock
    private PizzeriaUserRepository userRepository;

    @Mock
    private PurchaseRepository purchaseRepository;

    private PurchaseService purchaseService;

    private PizzeriaUser currentUser;

    @BeforeEach
    void setUp() {
        purchaseService = new PurchaseService(purchaseRepository, userRepository);
        currentUser = new PizzeriaUser();
        currentUser.setName("Papa");
        currentUser.setId(666L);
        currentUser.setRoles(Collections.emptyList());
        currentUser.setEmail("abc@def.com");

        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        PizzeriaUserPrincipal userPrincipal = new PizzeriaUserPrincipal(currentUser);
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
    }

    @Test
    void testShouldPlaceCompletePurchaseWithPineappleOffer() {
        long id = 666L;

        Purchase purchase = getPurchase(PurchaseState.ONGOING);
        List<Pizza> pizzas = new ArrayList<>();
        pizzas.add(getPizza(1L, "Margherita", 10.00, "tomato", "mozzarella"));
        pizzas.add(getPizza(2L, "Ham-Pineapple", 12.89, "pepperoni", "pineapple", "mozzarella"));
        purchase.setPizzas(pizzas);

        Optional<Purchase> purchaseOption = Optional.of(purchase);

        when(purchaseRepository.findById(id)).thenReturn(purchaseOption);

        Map<PizzeriaUser, Purchase> map = (Map<PizzeriaUser, Purchase>) ReflectionTestUtils.getField(purchaseService, "ongoingPurchases");
        map.put(currentUser, purchase);

        purchaseService.completePurchase(id);

        assertEquals(purchase.getAmount(), 21.89);

        verify(purchaseRepository).findById(id);
        verify(purchaseRepository).save(purchase);
    }

    @Test
    void testShouldPlaceCompletePurchaseWithBuy3CheapestFreeOffer() {
        long id = 666L;

        Purchase purchase = getPurchase(PurchaseState.ONGOING);
        List<Pizza> pizzas = new ArrayList<>();
        pizzas.add(getPizza(1L, "Margherita", 10.00, "tomato", "mozzarella"));
        pizzas.add(getPizza(2L, "Ham", 12.89, "pepperoni", "mozzarella"));
        pizzas.add(getPizza(3L, "Cheese", 14.99, "Parmigiano", "mozzarella", "Cheddar"));
        purchase.setPizzas(pizzas);

        Optional<Purchase> purchaseOption = Optional.of(purchase);

        when(purchaseRepository.findById(id)).thenReturn(purchaseOption);

        Map<PizzeriaUser, Purchase> map = (Map<PizzeriaUser, Purchase>) ReflectionTestUtils.getField(purchaseService, "ongoingPurchases");
        map.put(currentUser, purchase);

        purchaseService.completePurchase(id);

        assertEquals(purchase.getAmount(), 27.88, 0.00);

        verify(purchaseRepository).findById(id);
        verify(purchaseRepository).save(purchase);
    }

    @Test
    void testShouldPlaceCompletePurchaseWithoutAnyOffer() {
        long id = 666L;

        Purchase purchase = getPurchase(PurchaseState.ONGOING);
        List<Pizza> pizzas = new ArrayList<>();
        pizzas.add(getPizza(1L, "Margherita", 10.05, "tomato", "mozzarella"));
        pizzas.add(getPizza(2L, "Ham", 12.87, "pepperoni", "mozzarella"));
        purchase.setPizzas(pizzas);

        Optional<Purchase> purchaseOption = Optional.of(purchase);

        when(purchaseRepository.findById(id)).thenReturn(purchaseOption);

        Map<PizzeriaUser, Purchase> map = (Map<PizzeriaUser, Purchase>) ReflectionTestUtils.getField(purchaseService, "ongoingPurchases");
        map.put(currentUser, purchase);

        purchaseService.completePurchase(id);

        assertEquals(purchase.getAmount(), 22.92);

        verify(purchaseRepository).findById(id);
        verify(purchaseRepository).save(purchase);
    }

    @Test
    void should_create_draft_purchase_if_non_existing() {
        purchaseService.addPizzaToPurchase(new Pizza());

        ArgumentCaptor<Purchase> purchaseCaptor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).save(purchaseCaptor.capture());
        Purchase saved = purchaseCaptor.getValue();
        assertThat(saved.getState()).isEqualByComparingTo(PurchaseState.DRAFT);
    }

    @Test
    void should_throw_exception_if_more_purchases() {
        when(purchaseRepository.findAllByStateEqualsAndCustomer_Id(any(), any()))
                .thenReturn(Arrays.asList(new Purchase(), new Purchase()));

        assertThrows(PizzeriaException.class, () -> purchaseService.addPizzaToPurchase(new Pizza()));
    }

    @Test
    void confirm_purchase_changes_state() {
        when(purchaseRepository.findAllByStateEqualsAndCustomer_Id(any(), any()))
                .thenReturn(Collections.singletonList(new Purchase()));

        purchaseService.confirmPurchase();

        ArgumentCaptor<Purchase> purchaseCaptor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).save(purchaseCaptor.capture());
        Purchase saved = purchaseCaptor.getValue();
        assertThat(saved.getState()).isEqualByComparingTo(PurchaseState.PLACED);
    }

    @Test
    void should_add_items_to_purchase() {
        Pizza one = new Pizza();
        one.setId(666L);
        Pizza two = new Pizza();
        two.setId(43L);
        purchaseService.addPizzaToPurchase(one);
        purchaseService.addPizzaToPurchase(two);
        Purchase toReturn = new Purchase();
        toReturn.setPizzas(Arrays.asList(one, two));
        when(purchaseRepository.findAllByStateEqualsAndCustomer_Id(any(), any()))
                .thenReturn(Collections.singletonList(new Purchase()));
        when(purchaseRepository.findFirstByStateEquals(any())).thenReturn(new Purchase());
        when(purchaseRepository.save(any())).thenReturn(toReturn);

        purchaseService.confirmPurchase();

        Purchase latest = purchaseService.pickPurchase();
        assertThat(latest.getPizzas()).containsExactlyInAnyOrder(one, two);
    }

    @Test
    void confirm_pick_changes_state() {
        when(purchaseRepository.findFirstByStateEquals(any()))
                .thenReturn(new Purchase());

        purchaseService.pickPurchase();

        ArgumentCaptor<Purchase> purchaseCaptor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).save(purchaseCaptor.capture());
        Purchase saved = purchaseCaptor.getValue();
        assertThat(saved.getState()).isEqualByComparingTo(PurchaseState.ONGOING);
    }

    @Test
    void confirm_close_changes_state() {
        Purchase p = new Purchase();
        p.setState(PurchaseState.ONGOING);
        when(purchaseRepository.findFirstByStateEquals(any()))
                .thenReturn(p);
        when(purchaseRepository.findById(any()))
                .thenReturn(Optional.of(p));
        purchaseService.pickPurchase();

        purchaseService.completePurchase(p.getId());

        ArgumentCaptor<Purchase> purchaseCaptor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository, times(2)).save(purchaseCaptor.capture());
        Purchase saved = purchaseCaptor.getValue();
        assertThat(saved.getState()).isEqualByComparingTo(PurchaseState.SERVED);
    }

    private Purchase getPurchase(PurchaseState state) {
        Purchase purchase = new Purchase();
        purchase.setState(state);
        return purchase;
    }

    private Pizza getPizza(long id, String name, double price, String... toppings) {
        Pizza pizza = new Pizza();
        pizza.setId(id);
        pizza.setName(name);
        pizza.setToppings(Arrays.asList(toppings));
        pizza.setPrice(price);
        return pizza;
    }
}
