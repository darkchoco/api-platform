package darkchoco.apmapi.portfolio.controller;

import darkchoco.apmapi.portfolio.dto.ItemResponse;
import darkchoco.apmapi.portfolio.service.ItemService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
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
}
