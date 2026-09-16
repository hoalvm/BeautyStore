package t4m.beauty_store.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import t4m.beauty_store.chatbot.dto.IntentClassification;

import java.text.Normalizer;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rule-based intent and beauty-profile slot recognition for Vietnamese messages. */
@Service
public class IntentRecognitionService {

    private static final Logger logger = LoggerFactory.getLogger(IntentRecognitionService.class);
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern BUDGET_PATTERN = Pattern.compile(
            "\\b(\\d+(?:[.,]\\d+)?)\\s*(k|nghin|trieu|tr|dong|vnd)\\b");
    private static final Pattern SPF_PATTERN = Pattern.compile("\\bspf\\s*(\\d{2,3})(?:\\+)?\\b");

    private static final Map<IntentClassification.Intent, String[]> INTENT_KEYWORDS =
            new EnumMap<>(IntentClassification.Intent.class);

    static {
        INTENT_KEYWORDS.put(IntentClassification.Intent.SAFETY_INQUIRY,
                new String[]{"kich ung", "di ung", "bong rat", "sung mat", "kho tho", "mang thai",
                        "cho con bu", "benh da", "mun viem nang", "viem da", "bac si"});
        INTENT_KEYWORDS.put(IntentClassification.Intent.HUMAN_HANDOFF,
                new String[]{"nhan vien", "nguoi that", "gap tu van vien", "lien he", "hotline"});
        INTENT_KEYWORDS.put(IntentClassification.Intent.COMPLAINT,
                new String[]{"khieu nai", "khong hai long", "hang loi", "giao sai", "vo hong", "ro ri"});
        INTENT_KEYWORDS.put(IntentClassification.Intent.ROUTINE_RECOMMENDATION,
                new String[]{"routine", "chu trinh", "cac buoc", "buoc nao truoc", "skincare sang", "skincare toi"});
        INTENT_KEYWORDS.put(IntentClassification.Intent.PRODUCT_RECOMMENDATION,
                new String[]{"tu van", "goi y", "de xuat", "nen dung", "nen mua", "phu hop", "combo", "bo san pham"});
        INTENT_KEYWORDS.put(IntentClassification.Intent.INGREDIENT_INQUIRY,
                new String[]{"thanh phan", "inci", "hoat chat", "nong do", "retinol", "niacinamide",
                        "vitamin c", "bha", "aha", "ceramide", "peptide"});
        INTENT_KEYWORDS.put(IntentClassification.Intent.SHADE_MATCHING,
                new String[]{"chon mau", "chon tone", "tong da", "undertone", "shade", "mau son", "mau nen"});
        INTENT_KEYWORDS.put(IntentClassification.Intent.PRODUCT_SEARCH,
                new String[]{"tim", "co ban", "shop co", "sua rua mat", "serum", "kem duong", "kem chong nang",
                        "son", "phan", "nuoc hoa", "dau goi", "mat na"});
        INTENT_KEYWORDS.put(IntentClassification.Intent.PRICE_INQUIRY,
                new String[]{"gia bao nhieu", "bao nhieu tien", "gia", "price", "cost"});
        INTENT_KEYWORDS.put(IntentClassification.Intent.AVAILABILITY_CHECK,
                new String[]{"con hang", "con khong", "het hang", "ton kho", "available", "stock"});
        INTENT_KEYWORDS.put(IntentClassification.Intent.POLICY_INQUIRY,
                new String[]{"chinh sach", "doi tra", "giao hang", "van chuyen", "hoan tien", "doi hang"});
        INTENT_KEYWORDS.put(IntentClassification.Intent.CATEGORY_BROWSE,
                new String[]{"danh muc", "loai my pham", "co nhung gi", "xem san pham"});
        INTENT_KEYWORDS.put(IntentClassification.Intent.PROMOTION_INQUIRY,
                new String[]{"dang sale", "khuyen mai", "giam gia", "sale", "discount", "voucher", "uu dai"});
        INTENT_KEYWORDS.put(IntentClassification.Intent.ORDER_TRACKING,
                new String[]{"don hang", "ma don", "theo doi", "giao toi dau", "tracking"});
    }

    public IntentClassification classifyIntent(String message, String conversationContext) {
        String normalizedMessage = normalize(message);
        Map<IntentClassification.Intent, Double> scores = new EnumMap<>(IntentClassification.Intent.class);
        INTENT_KEYWORDS.forEach((intent, keywords) -> {
            double score = calculateIntentScore(normalizedMessage, keywords);
            if (score > 0) {
                scores.put(intent, score);
            }
        });

        IntentClassification.Intent bestIntent = IntentClassification.Intent.GENERAL_INQUIRY;
        double bestScore = 0.0;
        for (Map.Entry<IntentClassification.Intent, Double> entry : scores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestIntent = entry.getKey();
                bestScore = entry.getValue();
            }
        }

        // Safety and explicit handoff always take precedence over commercial suggestions.
        if (scores.containsKey(IntentClassification.Intent.SAFETY_INQUIRY)) {
            bestIntent = IntentClassification.Intent.SAFETY_INQUIRY;
            bestScore = Math.max(bestScore, 0.9);
        } else if (scores.containsKey(IntentClassification.Intent.HUMAN_HANDOFF)) {
            bestIntent = IntentClassification.Intent.HUMAN_HANDOFF;
            bestScore = Math.max(bestScore, 0.9);
        } else if (bestScore < 0.25 && conversationContext != null && !conversationContext.isBlank()) {
            bestIntent = inferFromContext(normalize(conversationContext));
            bestScore = 0.35;
        }

        IntentClassification classification = new IntentClassification();
        classification.setIntent(bestIntent);
        classification.setConfidence(Math.min(1.0, bestScore));
        extractSlots(normalizedMessage, classification);
        if (bestIntent == IntentClassification.Intent.PRODUCT_SEARCH && message != null) {
            classification.setSlot(IntentClassification.SLOT_PRODUCT_NAME, message.strip());
        }

        logger.debug("BeautyStore intent {} ({}) with {} extracted slots",
                bestIntent, bestScore, classification.getSlots().size());
        return classification;
    }

    private double calculateIntentScore(String message, String[] keywords) {
        double score = 0.0;
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                score += keyword.contains(" ") ? 0.45 : 0.22;
            }
        }
        return score;
    }

    private IntentClassification.Intent inferFromContext(String context) {
        if (containsAny(context, "doi tra", "giao hang", "hoan tien")) {
            return IntentClassification.Intent.POLICY_INQUIRY;
        }
        if (containsAny(context, "routine", "chu trinh", "buoc skincare")) {
            return IntentClassification.Intent.ROUTINE_RECOMMENDATION;
        }
        if (containsAny(context, "loai da", "van de da", "ngan sach", "loai toc")) {
            return IntentClassification.Intent.PRODUCT_RECOMMENDATION;
        }
        return IntentClassification.Intent.GENERAL_INQUIRY;
    }

    private void extractSlots(String message, IntentClassification classification) {
        extractSkinType(message, classification);
        extractConcern(message, classification);
        extractHairProfile(message, classification);
        extractIngredient(message, classification);
        extractCategory(message, classification);
        extractFinishAndShade(message, classification);
        extractRoutineStep(message, classification);
        extractBudget(message, classification);

        Matcher spfMatcher = SPF_PATTERN.matcher(message);
        if (spfMatcher.find()) {
            classification.setSlot(IntentClassification.SLOT_SPF, "SPF " + spfMatcher.group(1));
        }
        if (containsAny(message, "gia re", "tiet kiem", "binh dan")) {
            classification.setSlot(IntentClassification.SLOT_PRICE_RANGE, "low");
        } else if (containsAny(message, "cao cap", "premium", "luxury")) {
            classification.setSlot(IntentClassification.SLOT_PRICE_RANGE, "high");
        }
    }

    private void extractSkinType(String message, IntentClassification classification) {
        if (containsAny(message, "da dau", "dau mun")) {
            classification.setSlot(IntentClassification.SLOT_SKIN_TYPE, "oily");
        } else if (message.contains("da kho")) {
            classification.setSlot(IntentClassification.SLOT_SKIN_TYPE, "dry");
        } else if (containsAny(message, "da hon hop", "vung chu t")) {
            classification.setSlot(IntentClassification.SLOT_SKIN_TYPE, "combination");
        } else if (containsAny(message, "da nhay cam", "de kich ung")) {
            classification.setSlot(IntentClassification.SLOT_SKIN_TYPE, "sensitive");
        } else if (message.contains("da thuong")) {
            classification.setSlot(IntentClassification.SLOT_SKIN_TYPE, "normal");
        }
    }

    private void extractConcern(String message, IntentClassification classification) {
        if (containsAny(message, "mun", "bi tac", "dau den")) {
            classification.setSlot(IntentClassification.SLOT_CONCERN, "acne");
        } else if (containsAny(message, "tham", "nam", "tan nhang", "khong deu mau")) {
            classification.setSlot(IntentClassification.SLOT_CONCERN, "dark-spots");
        } else if (containsAny(message, "lao hoa", "nep nhan", "chay xe")) {
            classification.setSlot(IntentClassification.SLOT_CONCERN, "aging");
        } else if (containsAny(message, "thieu am", "mat nuoc", "bong troc")) {
            classification.setSlot(IntentClassification.SLOT_CONCERN, "dehydration");
        } else if (containsAny(message, "lo chan long", "lo chan long to")) {
            classification.setSlot(IntentClassification.SLOT_CONCERN, "pores");
        } else if (containsAny(message, "xam", "xi mau", "kem rang ro")) {
            classification.setSlot(IntentClassification.SLOT_CONCERN, "dullness");
        } else if (containsAny(message, "do da", "ung do")) {
            classification.setSlot(IntentClassification.SLOT_CONCERN, "redness");
        }
    }

    private void extractHairProfile(String message, IntentClassification classification) {
        if (message.contains("toc dau")) {
            classification.setSlot(IntentClassification.SLOT_HAIR_TYPE, "oily");
        } else if (message.contains("toc kho")) {
            classification.setSlot(IntentClassification.SLOT_HAIR_TYPE, "dry");
        } else if (containsAny(message, "toc xoan", "toc uon")) {
            classification.setSlot(IntentClassification.SLOT_HAIR_TYPE, "curly");
        } else if (containsAny(message, "toc nhuom", "toc tay")) {
            classification.setSlot(IntentClassification.SLOT_HAIR_TYPE, "color-treated");
        }

        if (containsAny(message, "rụng tóc", "rung toc")) {
            classification.setSlot(IntentClassification.SLOT_HAIR_CONCERN, "hair-loss");
        } else if (message.contains("gau")) {
            classification.setSlot(IntentClassification.SLOT_HAIR_CONCERN, "dandruff");
        } else if (containsAny(message, "hu ton", "che ngon", "kho xo")) {
            classification.setSlot(IntentClassification.SLOT_HAIR_CONCERN, "damage");
        }
    }

    private void extractIngredient(String message, IntentClassification classification) {
        Map<String, String> ingredients = new LinkedHashMap<>();
        ingredients.put("hyaluronic acid", "hyaluronic-acid");
        ingredients.put("vitamin c", "vitamin-c");
        ingredients.put("niacinamide", "niacinamide");
        ingredients.put("salicylic acid", "salicylic-acid");
        ingredients.put("retinol", "retinol");
        ingredients.put("bakuchiol", "bakuchiol");
        ingredients.put("ceramide", "ceramide");
        ingredients.put("peptide", "peptide");
        ingredients.put("bha", "bha");
        ingredients.put("aha", "aha");
        ingredients.put("cica", "cica");
        ingredients.entrySet().stream()
                .filter(entry -> message.contains(entry.getKey()))
                .findFirst()
                .ifPresent(entry -> classification.setSlot(
                        IntentClassification.SLOT_INGREDIENT, entry.getValue()));
    }

    private void extractCategory(String message, IntentClassification classification) {
        if (containsAny(message, "mini", "minisize", "qua tang", "gift set", "bo qua")) {
            classification.setSlot(IntentClassification.SLOT_CATEGORY, "Bộ quà tặng & minisize");
        } else if (containsAny(message, "co trang diem", "bong mut", "kep mi", "dung cu")) {
            classification.setSlot(IntentClassification.SLOT_CATEGORY, "Dụng cụ & phụ kiện");
        } else if (containsAny(message, "nuoc hoa", "mui huong", "perfume")) {
            classification.setSlot(IntentClassification.SLOT_CATEGORY, "Nước hoa");
        } else if (containsAny(message, "dau goi", "dau xa", "toc", "tri gau")) {
            classification.setSlot(IntentClassification.SLOT_CATEGORY, "Chăm sóc tóc");
        } else if (containsAny(message, "sua tam", "duong the", "tay da chet body", "co the")) {
            classification.setSlot(IntentClassification.SLOT_CATEGORY, "Chăm sóc cơ thể");
        } else if (containsAny(message, "son", "phan", "kem nen", "mascara", "trang diem")) {
            classification.setSlot(IntentClassification.SLOT_CATEGORY, "Trang điểm");
        } else if (containsAny(message, "chong nang", "spf", "pa++++")) {
            classification.setSlot(IntentClassification.SLOT_CATEGORY, "Chống nắng");
        } else if (containsAny(message, "sua rua mat", "serum", "kem duong", "toner", "mat na", "skincare")) {
            classification.setSlot(IntentClassification.SLOT_CATEGORY, "Chăm sóc da mặt");
        }
    }

    private void extractFinishAndShade(String message, IntentClassification classification) {
        if (containsAny(message, "li", "matte")) {
            classification.setSlot(IntentClassification.SLOT_FINISH, "matte");
        } else if (containsAny(message, "cang bong", "dewy", "glowy")) {
            classification.setSlot(IntentClassification.SLOT_FINISH, "dewy");
        } else if (containsAny(message, "tu nhien", "natural finish")) {
            classification.setSlot(IntentClassification.SLOT_FINISH, "natural");
        }

        if (containsAny(message, "tone am", "undertone am", "warm tone")) {
            classification.setSlot(IntentClassification.SLOT_SHADE, "warm");
        } else if (containsAny(message, "tone lanh", "undertone lanh", "cool tone")) {
            classification.setSlot(IntentClassification.SLOT_SHADE, "cool");
        } else if (containsAny(message, "trung tinh", "neutral")) {
            classification.setSlot(IntentClassification.SLOT_SHADE, "neutral");
        } else if (containsAny(message, "do dat", "do nau", "hong dat", "cam dat")) {
            classification.setSlot(IntentClassification.SLOT_SHADE, "earth-tone");
        }
    }

    private void extractRoutineStep(String message, IntentClassification classification) {
        if (containsAny(message, "tay trang", "sua rua mat", "lam sach")) {
            classification.setSlot(IntentClassification.SLOT_ROUTINE_STEP, "cleanse");
        } else if (containsAny(message, "serum", "dac tri", "treatment")) {
            classification.setSlot(IntentClassification.SLOT_ROUTINE_STEP, "treat");
        } else if (containsAny(message, "kem duong", "duong am")) {
            classification.setSlot(IntentClassification.SLOT_ROUTINE_STEP, "moisturize");
        } else if (containsAny(message, "chong nang", "spf")) {
            classification.setSlot(IntentClassification.SLOT_ROUTINE_STEP, "protect");
        }
    }

    private void extractBudget(String message, IntentClassification classification) {
        Matcher matcher = BUDGET_PATTERN.matcher(message);
        if (!matcher.find()) {
            return;
        }
        try {
            double amount = Double.parseDouble(matcher.group(1).replace(',', '.'));
            String unit = matcher.group(2);
            if (unit.equals("k") || unit.equals("nghin")) {
                amount *= 1_000;
            } else if (unit.equals("tr") || unit.equals("trieu")) {
                amount *= 1_000_000;
            }
            if (amount > 0 && amount <= Integer.MAX_VALUE) {
                classification.setSlot(IntentClassification.SLOT_MAX_BUDGET, (int) Math.round(amount));
            }
        } catch (NumberFormatException ignored) {
            logger.debug("Could not parse a budget slot");
        }
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT).strip();
    }
}
