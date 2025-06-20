package exa.org.cg;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class FallBack {
    public static Map<String, String> loadConfigMapping(Path confDir) {
        Map<String, String> map = new HashMap<>();
        try {
            // 1) Struts - *.xml bean
            Files.walk(confDir)
                    .filter(p -> p.toString().endsWith(".xml"))
                    .forEach(p -> parseStrutsBeanXml(p.toFile(), map));

            // 2) properties 常量
            Files.walk(confDir)
                    .filter(p -> p.toString().endsWith(".properties"))
                    .forEach(p -> parseProperties(p.toFile(), map));

        } catch (Exception e) {
            System.err.println("[CFG] load failed: " + e);
        }
        return map;
    }
    private static void parseStrutsBeanXml(File f, Map<String, String> map) {
        try {
            System.out.println("Parsing " + f);
            Document doc = Jsoup.parse(f, "UTF-8");
            for (Element bean : doc.select("bean")) {
                String type = bean.attr("type").trim();
                String name = bean.attr("name").trim();
                String impl = bean.attr("class").trim();
                if (!type.isEmpty() && !impl.isEmpty())
                    map.put(type + "#" + name, impl); // 接口+name → impl
            }
        } catch (Exception ignore) {
        }
    }

    private static void parseProperties(File f, Map<String, String> map) {
        try {
            System.out.println("Parsing " + f);
            Properties prop = new Properties();
            prop.load(Files.newInputStream(f.toPath()));
            String parser = prop.getProperty("struts.multipart.parser");
            if (parser != null)
                map.put("struts.multipart.parser", parser.trim());
        } catch (Exception ignore) {
        }
    }
    public static void main(String[] args) {
        Path path = Paths.get("../benchmark/struts-045");
        Map<String, String> map = loadConfigMapping(path);
        for (Map.Entry<String,String> entry : map.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();
            System.out.printf("%s  =>  %s\n", key, val);
        }
    }
}
