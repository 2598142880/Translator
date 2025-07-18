package kgg.translator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import kgg.translator.event.SetTranslatorEvent;
import kgg.translator.translator.LLMTranslator;
import kgg.translator.translator.LLMTranslatorImpl;
import kgg.translator.translator.Translator;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class LLMManager {
    private static final Map<String, Model> models = new LinkedHashMap<>();
    private static String prompt;
    
    static {
        // 加载自定义提示词
        Path promptPath = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("translator")
            .resolve("prompt.txt");
        
        if (Files.exists(promptPath)) {
            try {
                prompt = Files.readString(promptPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * LLM 模型配置
     */
    public static class Model {
        public String name;
        public String url;
        public String model;
        public String apiKey;
        public int qps; // 添加 QPS 字段
        
        public Model(String name, String url, String model, String apiKey) {
            this(name, url, model, apiKey, 10); // 默认 QPS 为 10
        }
        
        public Model(String name, String url, String model, String apiKey, int qps) {
            this.name = name;
            this.url = url;
            this.model = model;
            this.apiKey = apiKey;
            this.qps = qps;
        }
        
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", name);
            obj.addProperty("url", url);
            obj.addProperty("model", model);
            obj.addProperty("apiKey", apiKey);
            obj.addProperty("qps", qps);
            return obj;
        }
        
        public static Model fromJson(JsonObject obj) {
            String name = obj.get("name").getAsString();
            String url = obj.get("url").getAsString();
            String model = obj.get("model").getAsString();
            String apiKey = obj.get("apiKey").getAsString();
            int qps = obj.has("qps") ? obj.get("qps").getAsInt() : 10; // 兼容旧配置
            return new Model(name, url, model, apiKey, qps);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Model model1 = (Model) o;
            return qps == model1.qps &&
                   Objects.equals(name, model1.name) &&
                   Objects.equals(url, model1.url) &&
                   Objects.equals(model, model1.model) &&
                   Objects.equals(apiKey, model1.apiKey);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(name, url, model, apiKey, qps);
        }
    }
    
    /**
     * 获取内置的模型模板
     */
    public static Model[] geBuiltInModels() {
        return new Model[] {
            new Model("OpenAI GPT-4o", "https://api.openai.com/v1/chat/completions", "gpt-4o", "YOUR_API_KEY", 10),
            new Model("OpenAI GPT-4o-mini", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini", "YOUR_API_KEY", 30),
            new Model("OpenAI GPT-3.5-turbo", "https://api.openai.com/v1/chat/completions", "gpt-3.5-turbo", "YOUR_API_KEY", 60),
            new Model("Claude 3.5 Sonnet", "https://api.anthropic.com/v1/messages", "claude-3-5-sonnet-20241022", "YOUR_API_KEY", 20),
            new Model("Claude 3.5 Haiku", "https://api.anthropic.com/v1/messages", "claude-3-5-haiku-20241022", "YOUR_API_KEY", 30),
            new Model("Gemini Pro", "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent", "gemini-pro", "YOUR_API_KEY", 15),
            new Model("DeepSeek Chat", "https://api.deepseek.com/v1/chat/completions", "deepseek-chat", "YOUR_API_KEY", 50),
            new Model("DeepSeek Coder", "https://api.deepseek.com/v1/chat/completions", "deepseek-coder", "YOUR_API_KEY", 50),
            new Model("Qwen Plus", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-plus", "YOUR_API_KEY", 40),
            new Model("Qwen Turbo", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-turbo", "YOUR_API_KEY", 60),
            new Model("Kimi Moonshot", "https://api.moonshot.cn/v1/chat/completions", "moonshot-v1-8k", "YOUR_API_KEY", 20)
        };
    }
    
    /**
     * 添加模型
     */
    public static void addModel(Model model) {
        // 检查是否存在同名的翻译器
        Translator existingTranslator = null;
        for (Translator translator : TranslatorManager.getTranslators()) {
            if (translator.getName().equals(model.name)) {
                existingTranslator = translator;
                break;
            }
        }
        
        // 如果存在且当前正在使用，先记录下来
        boolean wasCurrentTranslator = existingTranslator != null && 
                                      TranslatorManager.getCurrent() == existingTranslator;
        
        // 移除旧的翻译器
        if (existingTranslator != null) {
            TranslatorManager.getTranslators().remove(existingTranslator);
        }
        
        // 添加或更新模型
        models.put(model.name, model);
        
        // 创建新的翻译器
        LLMTranslator newTranslator = new LLMTranslatorImpl(model);
        TranslatorManager.addTranslator(newTranslator);
        
        // 如果之前是当前翻译器，重新设置为当前
        if (wasCurrentTranslator) {
            TranslatorManager.setTranslator(newTranslator);
            SetTranslatorEvent.invoke(newTranslator);
        }
    }
    
    /**
     * 移除模型
     */
    public static boolean removeModel(String name) {
        Model removed = models.remove(name);
        if (removed != null) {
            // 从翻译器列表中移除
            TranslatorManager.getTranslators().removeIf(t -> 
                t instanceof LLMTranslator && t.getName().equals(name)
            );
            return true;
        }
        return false;
    }
    
    /**
     * 获取所有模型
     */
    public static Map<String, Model> getModels() {
        return models;
    }
    
    /**
     * 从 JSON 加载配置
     */
    public static void loadFromJson(JsonObject json) {
        models.clear();
        
        if (json.has("llm_models")) {
            JsonObject modelsObj = json.getAsJsonObject("llm_models");
            for (Map.Entry<String, JsonElement> entry : modelsObj.entrySet()) {
                Model model = Model.fromJson(entry.getValue().getAsJsonObject());
                models.put(model.name, model);
                
                // 创建对应的翻译器
                TranslatorManager.addTranslator(new LLMTranslatorImpl(model));
            }
        }
    }
    
    /**
     * 保存到 JSON
     */
    public static void saveToJson(JsonObject json) {
        JsonObject modelsObj = new JsonObject();
        for (Map.Entry<String, Model> entry : models.entrySet()) {
            modelsObj.add(entry.getKey(), entry.getValue().toJson());
        }
        json.add("llm_models", modelsObj);
    }
    
    /**
     * 获取自定义提示词
     */
    public static String getPrompt() {
        return prompt;
    }
}