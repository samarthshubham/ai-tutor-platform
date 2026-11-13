package io.github.samarthshubham.tutor.ai.prompt;

import io.github.samarthshubham.tutor.ai.api.AiFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

public class PromptCatalog {

  public static final String DEFAULT_LOCATION = "classpath:prompts/";

  private static final Pattern FILE_NAME = Pattern.compile("([a-z0-9-]+)\\.v([1-9][0-9]*)\\.st");
  private static final Pattern FEATURE_LINE = Pattern.compile("feature: ([A-Z_]+)");

  private final String location;
  private final Map<String, VersionedPrompt> newestByName = new HashMap<>();

  public PromptCatalog(ResourcePatternResolver resolver, String location) {
    this.location = location;
    for (Resource resource : resources(resolver, location + "*.st")) {
      add(resource);
    }
  }

  public VersionedPrompt get(String name) {
    VersionedPrompt prompt = newestByName.get(name);
    if (prompt == null) {
      throw new IllegalStateException("No prompt named '" + name + "' in " + location);
    }
    return prompt;
  }

  private void add(Resource resource) {
    String fileName = resource.getFilename();
    Matcher matcher = FILE_NAME.matcher(fileName == null ? "" : fileName);
    if (!matcher.matches()) {
      throw new IllegalStateException(
          "Prompt file " + fileName + " is not named <name>.v<number>.st");
    }
    String name = matcher.group(1);
    int number = Integer.parseInt(matcher.group(2));
    String template = read(resource);
    requireFeatureLine(fileName, template);
    VersionedPrompt current = newestByName.get(name);
    if (current == null || current.number() < number) {
      newestByName.put(name, new VersionedPrompt(name + ".v" + number, number, template));
    }
  }

  private static void requireFeatureLine(String fileName, String template) {
    Matcher matcher = FEATURE_LINE.matcher(template.lines().findFirst().orElse(""));
    boolean known =
        matcher.matches()
            && Arrays.stream(AiFeature.values()).anyMatch(f -> f.name().equals(matcher.group(1)));
    if (!known) {
      throw new IllegalStateException(
          "Prompt file " + fileName + " must start with a line 'feature: <AiFeature>'");
    }
  }

  private static Resource[] resources(ResourcePatternResolver resolver, String pattern) {
    try {
      return resolver.getResources(pattern);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot list prompts at " + pattern, e);
    }
  }

  private static String read(Resource resource) {
    try {
      return resource.getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read prompt " + resource.getFilename(), e);
    }
  }

  public record VersionedPrompt(String version, int number, String template) {}
}
