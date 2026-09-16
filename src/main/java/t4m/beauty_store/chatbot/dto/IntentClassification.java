package t4m.beauty_store.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Intent Classification - Step 2: Natural Language Processing
 * Phân loại mục đích và thu thập thông số từ câu hỏi của khách hàng
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntentClassification {
    
    // Intent types for BeautyStore cosmetics commerce
    public enum Intent {
        PRODUCT_RECOMMENDATION,  // Tư vấn sản phẩm
        ROUTINE_RECOMMENDATION,  // Tư vấn chu trình chăm sóc
        PRODUCT_SEARCH,          // Tìm kiếm sản phẩm cụ thể
        PRICE_INQUIRY,           // Hỏi giá
        AVAILABILITY_CHECK,      // Kiểm tra còn hàng
        POLICY_INQUIRY,          // Hỏi chính sách (đổi trả, giao hàng)
        INGREDIENT_INQUIRY,      // Hỏi về thành phần
        SHADE_MATCHING,          // Chọn tông/màu trang điểm
        SAFETY_INQUIRY,          // Kích ứng, thai kỳ hoặc bệnh lý da
        CATEGORY_BROWSE,         // Xem danh mục
        PROMOTION_INQUIRY,       // Hỏi khuyến mãi
        ORDER_TRACKING,          // Theo dõi đơn hàng
        COMPLAINT,               // Khiếu nại
        GENERAL_INQUIRY,         // Câu hỏi chung
        HUMAN_HANDOFF           // Yêu cầu chuyển nhân viên
    }
    
    private Intent intent;
    private double confidence; // 0.0 - 1.0
    
    // Slots - extracted parameters
    private Map<String, Object> slots = new HashMap<>();
    
    // Helper methods to set slots
    public void setSlot(String key, Object value) {
        slots.put(key, value);
    }
    
    public Object getSlot(String key) {
        return slots.get(key);
    }
    
    public String getSlotAsString(String key) {
        Object value = slots.get(key);
        return value != null ? value.toString() : null;
    }
    
    public Integer getSlotAsInteger(String key) {
        Object value = slots.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    // Common slot keys
    public static final String SLOT_CATEGORY = "category";              // String
    public static final String SLOT_PRODUCT_NAME = "product_name";      // String
    public static final String SLOT_PRICE_RANGE = "price_range";        // "low" / "medium" / "high"
    public static final String SLOT_MAX_BUDGET = "max_budget";          // Integer VND
    public static final String SLOT_SKIN_TYPE = "skin_type";            // Taxonomy code
    public static final String SLOT_CONCERN = "concern";                // Taxonomy code
    public static final String SLOT_HAIR_TYPE = "hair_type";            // Taxonomy code
    public static final String SLOT_HAIR_CONCERN = "hair_concern";      // Taxonomy code
    public static final String SLOT_INGREDIENT = "ingredient";          // Ingredient keyword
    public static final String SLOT_FORM = "form";                      // Taxonomy code
    public static final String SLOT_FINISH = "finish";                  // Taxonomy code
    public static final String SLOT_SHADE = "shade";                    // Shade/tone keyword
    public static final String SLOT_SPF = "spf";                        // SPF request
    public static final String SLOT_ROUTINE_STEP = "routine_step";      // cleanse/treat/moisturize/protect
}
