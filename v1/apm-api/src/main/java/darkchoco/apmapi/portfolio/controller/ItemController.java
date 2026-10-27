package darkchoco.apmapi.portfolio.controller;

import darkchoco.apmapi.portfolio.dto.ItemPlanRequest;
import darkchoco.apmapi.portfolio.dto.ItemResponse;
import darkchoco.apmapi.portfolio.service.ItemService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public List<ItemResponse> getItems(@RequestParam(required = false) Integer schemeAcId) {
        return itemService.getItems(schemeAcId);
    }

    @PutMapping("/{productId}/plan")
    public ItemResponse updatePlan(@PathVariable int productId, @RequestBody ItemPlanRequest request) {
        return itemService.updatePlan(productId, request.planAmount());
    }
}
