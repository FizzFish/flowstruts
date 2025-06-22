package exa.org.cg;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 解析 Struts/Spring 等 IoC 配置：
 *   1) bean(type,name)->class
 *   2) constant key->value
 *
 * 支持快速查询：接口 + 常量键  → 默认实现类
 */
public class IoCConfigLoader {

    /* ---------- 数据结构 ---------- */

    /**  type  ->  ( name -> implClass ) */
    private final Map<String, Map<String,String>> beanTable = new HashMap<>();
    private final Map<String, Set<String>> beanMap = new HashMap<>();
    /**  constant  key -> value */
    private final Map<String,String> constTable = new HashMap<>();

    public IoCConfigLoader(Path confDir) {
        parse(confDir);
    }

    public Map<String, Set<String>> getBeanMap() {
        return beanMap;
    }

    /* ---------- 入口：加载目录 ---------- */

    public void parse(Path confDir) {
        try {
            // 1. XML -> bean
            Files.walk(confDir)
                    .filter(p -> p.toString().endsWith(".xml"))
                    .forEach(p -> parseXmlBeans(p.toFile()));

            // 2. properties -> constant
            Files.walk(confDir)
                    .filter(p -> p.toString().endsWith(".properties"))
                    .forEach(p -> parseProperties(p.toFile()));

            beanTable.forEach((type, val) -> {
                String impl = getDefaultImpl(type);
                Set<String> set = new HashSet<>();
                if (impl != null)
                    set.add(impl);
                else
                    set.addAll(val.values());
                beanMap.put(type, set);
            });
        } catch (IOException e) {
            System.err.println("[CFG] scanning error: " + e);
        }
    }

    /* ---------- XML 解析 ---------- */

    private void parseXmlBeans(File xml) {
        try {
            Document doc = Jsoup.parse(xml, "UTF-8");
            for (Element bean : doc.select("bean")) {
                String type  = bean.attr("type").trim();
                if (type.isEmpty()) continue;

                String name  = bean.hasAttr("name") ? bean.attr("name").trim()
                        : "default";
                String clazz = bean.attr("class").trim();
                if (clazz.isEmpty()) continue;

                beanTable
                        .computeIfAbsent(type, k -> new HashMap<>())
                        .put(name, clazz);
            }
        } catch (Exception ignore) { }
    }

    /* ---------- properties 解析 ---------- */

    private void parseProperties(File propFile) {
        try (InputStream in = Files.newInputStream(propFile.toPath())) {
            Properties p = new Properties();
            p.load(in);
            for (String key : p.stringPropertyNames())
                constTable.put(key.trim(), p.getProperty(key).trim());
        } catch (Exception ignore) { }
    }

    /**
     * 根据接口类型、常量键（如 struts.multipart.parser）
     * 推断“默认的”实现类
     *
     * @param type          接口全限定名
     */
    private String getDefaultImpl(String type) {
        Map<String,String> name2impl = beanTable.get(type);
        if (name2impl == null) return null;
        Collection<String> impls =  name2impl.values();
        if (impls.size() == 1)
            return impls.iterator().next();

        for (Map.Entry<String,String> entry : name2impl.entrySet()) {
            String key = entry.getKey();
            if (constTable.containsValue(key))
                return entry.getValue();
        }

        if (name2impl.containsKey("default"))
            return name2impl.get("default");

        if (name2impl.containsKey("struts"))
            return name2impl.get("struts");

        return name2impl.values().stream().findFirst().orElse(null);
    }

    /* ---------- demo main ---------- */

    public static void main(String[] args) {
        // 例如 confDir 指向 WEB-INF/classes 或 src/main/resources
        IoCConfigLoader cfg =
                 new IoCConfigLoader(Paths.get("../benchmark/struts-045"));

        Set<String> impl = cfg.getBeanMap().get("org.apache.struts2.dispatcher.multipart.MultiPartRequest");

        System.out.println("选择的 MultiPartRequest 实现 = " + impl);
        // 预期输出：org.apache.struts2.dispatcher.multipart.JakartaStreamMultiPartRequest
    }
}

