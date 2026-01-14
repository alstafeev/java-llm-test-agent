package agent.enums;

import java.util.Arrays;
import java.util.NoSuchElementException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TMSName {

  ALLURE,
  MANUAL;

  public static TMSName getByName(String tmsName) {
    return Arrays.stream(TMSName.values())
        .filter(indicator -> indicator.name().equalsIgnoreCase(tmsName))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException(String.format("TMS %s not available", tmsName)));
  }
}
