package com.mushroom.stockkeeper.controller;

import com.mushroom.stockkeeper.model.*;
import com.mushroom.stockkeeper.service.SalesService;
import com.mushroom.stockkeeper.service.SettingsService;
import com.mushroom.stockkeeper.repository.CustomerRepository;
import com.mushroom.stockkeeper.repository.SalesOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@AutoConfigureMockMvc
class SalesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SalesService salesService;
    @MockBean
    private CustomerRepository customerRepository;
    @MockBean
    private SalesOrderRepository orderRepository;
    @MockBean
    private SettingsService settingsService;
    @MockBean
    private com.mushroom.stockkeeper.repository.InventoryUnitRepository unitRepository;
    @MockBean
    private com.mushroom.stockkeeper.repository.InvoiceRepository invoiceRepository;
    @MockBean
    private com.mushroom.stockkeeper.repository.HarvestBatchRepository batchRepository;
    @MockBean
    private com.mushroom.stockkeeper.service.CustomUserDetailsService userDetailsService;
    @MockBean
    private com.mushroom.stockkeeper.service.AuditService auditService;

    @Test
    @WithMockUser(roles = "SALES")
    void createForm_ShouldReturnView() throws Exception {
        mockMvc.perform(get("/sales/create"))
                .andExpect(status().isOk())
                .andExpect(view().name("sales/create"))
                .andExpect(model().attributeExists("customers"));
    }

    @Test
    @WithMockUser(roles = "SALES")
    void createOrder_ShouldRedirectToPicking() throws Exception {
        SalesOrder draft = new SalesOrder();
        draft.setId(10L);
        when(salesService.createOrder(any(), anyString(), any())).thenReturn(draft);
        when(customerRepository.findById(anyLong())).thenReturn(Optional.of(new Customer()));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArguments()[0]); // Return the customer
                                                                                                 // itself

        mockMvc.perform(post("/sales/save")
                .param("customerId", "1")
                .param("orderType", "RETAIL")
                .param("paymentMethod", "CASH")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/sales/10"));
    }

    @Test
    @WithMockUser(roles = "SALES")
    void picking_ShouldReturnViewAndOrder() throws Exception {
        SalesOrder order = new SalesOrder();
        order.setId(10L);
        order.setStatus(SalesOrderStatus.DRAFT);
        order.setOrderNumber("SO-100");
        order.setOrderNumber("SO-100");
        order.setCustomer(new Customer()); // Set customer to avoid Template NPE
        order.setAllocatedUnits(new java.util.ArrayList<>()); // Initialize list to avoid NPE
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(invoiceRepository.findBySalesOrder(any())).thenReturn(Optional.empty());
        when(unitRepository.findByStatus(any())).thenReturn(new java.util.ArrayList<>());
        when(batchRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(new java.util.ArrayList<>());

        mockMvc.perform(get("/sales/10"))
                .andExpect(status().isOk())
                .andExpect(view().name("sales/picking"))
                .andExpect(model().attributeExists("order"));
    }

    @Test
    @WithMockUser(roles = "SALES")
    void allocateUnit_ShouldCallService() throws Exception {
        mockMvc.perform(post("/sales/10/allocate")
                .content("U:123")
                .with(csrf()))
                .andExpect(status().isOk());

        verify(salesService).allocateUnit(10L, "U:123");
    }

    @Test
    @WithMockUser(roles = "SALES")
    void finalize_ShouldRedirectToInvoice() throws Exception {
        Invoice invoice = new Invoice();
        invoice.setId(99L);
        when(salesService.finalizeOrder(anyLong(), anyBoolean(), anyString())).thenReturn(invoice);

        mockMvc.perform(post("/sales/10/finalize")
                .param("isPaid", "true")
                .param("paymentMethod", "CASH")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/sales/10")); // Controller redirects to /sales/{id} with flash attribute
    }
}
