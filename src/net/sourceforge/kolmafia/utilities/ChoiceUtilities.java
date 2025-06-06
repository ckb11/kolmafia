package net.sourceforge.kolmafia.utilities;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sourceforge.kolmafia.KoLmafia;
import net.sourceforge.kolmafia.RequestLogger;
import net.sourceforge.kolmafia.request.AdventureRequest;
import net.sourceforge.kolmafia.session.ChoiceAdventures;
import net.sourceforge.kolmafia.session.ChoiceAdventures.Spoilers;
import net.sourceforge.kolmafia.session.ChoiceManager;
import net.sourceforge.kolmafia.session.ChoiceOption;

/** Utilities for extracting data from a choice.php response */
public class ChoiceUtilities {
  private static final Pattern FORM_PATTERN = Pattern.compile("<form.*?</form>", Pattern.DOTALL);
  private static final Pattern OPTION_PATTERN1 =
      Pattern.compile("name=[\"']?option[\"']? value=[\"']?(\\d+)[\"']?");
  private static final Pattern TEXT_PATTERN1 =
      Pattern.compile("class=[\"']?button[\"']?.*?value=(?:\"([^\"]*)\"|'([^']*)'|([^ >]*))");

  private static final Pattern LINK_PATTERN = Pattern.compile("<[aA] .*?</[aA]>", Pattern.DOTALL);
  private static final Pattern OPTION_PATTERN2 = Pattern.compile("&option=(\\d+)");
  private static final Pattern TEXT_PATTERN2 =
      Pattern.compile("title=(?:\"([^\"]*)\"|'([^']*)'|([^ >]*))");

  private static final Pattern[] CHOICE_PATTERNS = {
    Pattern.compile("name=['\"]?whichchoice['\"]? value=['\"]?(\\d+)['\"]?"),
    Pattern.compile("value=['\"]?(\\d+)['\"]? name=['\"]?whichchoice['\"]?"),
    Pattern.compile("whichchoice=(\\d+)"),
  };

  private ChoiceUtilities() {}

  private static boolean isNonChoiceForm(final String form) {
    // We are searching for a form that submits to choice.php. With the assumption that a choice.php
    // page has been supplied, it must either have a choice.php action or no action at all.
    return !form.contains("choice.php") && !form.contains("<form method=\"post\">");
  }

  // Extract choice number from URL

  public static final Pattern URL_CHOICE_PATTERN = Pattern.compile("whichchoice=(\\d+)");

  public static int extractChoiceFromURL(final String urlString) {
    Matcher matcher = ChoiceUtilities.URL_CHOICE_PATTERN.matcher(urlString);
    return matcher.find() ? StringUtilities.parseInt(matcher.group(1)) : 0;
  }

  // Extract choice option from URL

  public static final Pattern URL_OPTION_PATTERN = Pattern.compile("(?<!force)option=(\\d+)");

  public static int extractOptionFromURL(final String urlString) {
    Matcher matcher = ChoiceUtilities.URL_OPTION_PATTERN.matcher(urlString);
    return matcher.find() ? StringUtilities.parseInt(matcher.group(1)) : 0;
  }

  // Extract choice number from responseText

  private static final Pattern DEV_READOUT = Pattern.compile("(.*?) \\(#\\d+\\)$");

  /**
   * On the dev server choice adventures have their choice numbers in brackets afterwards This needs
   * to be stripped
   *
   * @param encounter Raw encounter name
   * @return Encounter name without dev readout if it exists
   */
  public static String stripDevReadout(final String encounter) {
    if (!KoLmafia.usingDevServer()) return encounter;

    var m = DEV_READOUT.matcher(encounter);

    return m.find() ? m.group(1) : encounter;
  }

  public static int extractChoice(final String responseText) {
    for (Pattern pattern : ChoiceUtilities.CHOICE_PATTERNS) {
      Matcher matcher = pattern.matcher(responseText);
      if (matcher.find()) {
        return StringUtilities.parseInt(matcher.group(1));
      }
    }

    return switch (AdventureRequest.parseEncounter(responseText)) {
      case "Hippy Talkin'" ->
      // Is this really missing? My logs look normal - Veracity
      798;
      case "Another Errand I Mean Quest" -> 930;
      case "The WLF Bunker" -> 1093;
      case "Lyle, LyleCo CEO" -> 1309;
      case "What the Future Holds" -> 1462;
      case "Make a Wish" -> 1501;
      case "Research Bench" -> 1523;
      case "Foreseeing Peril" -> 1558;
      default -> 0;
    };
  }

  public static final Pattern DECISION_BUTTON_PATTERN =
      Pattern.compile(
          "<input type=hidden name=option value=(\\d+)>.*?<input +class=button type=submit value=\"(.*?)\">");

  public static String findChoiceDecisionIndex(final String text, final String responseText) {
    Matcher matcher = DECISION_BUTTON_PATTERN.matcher(responseText);
    while (matcher.find()) {
      String decisionText = matcher.group(2);

      if (decisionText.contains(text)) {
        return StringUtilities.getEntityDecode(matcher.group(1));
      }
    }

    return "0";
  }

  public static String findChoiceDecisionText(final int index, final String responseText) {
    Matcher matcher = DECISION_BUTTON_PATTERN.matcher(responseText);
    while (matcher.find()) {
      int decisionIndex = Integer.parseInt(matcher.group(1));

      if (decisionIndex == index) {
        return matcher.group(2);
      }
    }

    return null;
  }

  public static Map<Integer, String> parseChoices(final String responseText) {
    Map<Integer, String> rv = new TreeMap<>();
    if (responseText == null) {
      return rv;
    }

    Matcher m = FORM_PATTERN.matcher(responseText);
    while (m.find()) {
      String form = m.group();
      if (isNonChoiceForm(form)) continue;
      Matcher optMatcher = OPTION_PATTERN1.matcher(form);
      if (!optMatcher.find()) {
        continue;
      }
      var decision = Integer.parseInt(optMatcher.group(1));
      if (rv.get(decision) != null) {
        continue;
      }
      Matcher textMatcher = TEXT_PATTERN1.matcher(form);
      String text =
          !textMatcher.find()
              ? "(secret choice)"
              : textMatcher.group(1) != null
                  ? textMatcher.group(1)
                  : textMatcher.group(2) != null
                      ? textMatcher.group(2)
                      : textMatcher.group(3) != null ? textMatcher.group(3) : "(secret choice)";
      rv.put(decision, text);
    }

    m = LINK_PATTERN.matcher(responseText);
    while (m.find()) {
      String form = m.group();
      if (isNonChoiceForm(form)) continue;
      Matcher optMatcher = OPTION_PATTERN2.matcher(form);
      if (!optMatcher.find()) {
        continue;
      }
      var decision = Integer.parseInt(optMatcher.group(1));
      if (rv.get(decision) != null) {
        continue;
      }
      Matcher textMatcher = TEXT_PATTERN2.matcher(form);
      String text =
          !textMatcher.find()
              ? "(secret choice)"
              : textMatcher.group(1) != null
                  ? textMatcher.group(1)
                  : textMatcher.group(2) != null
                      ? textMatcher.group(2)
                      : textMatcher.group(3) != null ? textMatcher.group(3) : "(secret choice)";
      rv.put(decision, text);
    }

    return rv;
  }

  public static Map<Integer, String> parseChoicesWithSpoilers(final String responseText) {
    Map<Integer, String> rv = ChoiceUtilities.parseChoices(responseText);
    if (responseText == null) {
      return rv;
    }

    if (!ChoiceManager.handlingChoice) {
      return rv;
    }

    Spoilers possibleDecisions = ChoiceAdventures.choiceSpoilers(ChoiceManager.lastChoice, null);
    if (possibleDecisions == null) {
      return rv;
    }

    ChoiceOption[] options = possibleDecisions.getOptions();
    if (options == null) {
      return rv;
    }

    for (Map.Entry<Integer, String> entry : rv.entrySet()) {
      Integer key = entry.getKey();
      ChoiceOption option = ChoiceAdventures.findOption(options, key);
      if (option != null) {
        String text = entry.getValue() + " (" + option + ")";
        rv.put(key, text);
      }
    }

    return rv;
  }

  public static String actionOption(final String action, final String responseText) {
    Map<Integer, String> choices = ChoiceUtilities.parseChoices(responseText);
    for (Map.Entry<Integer, String> entry : choices.entrySet()) {
      if (entry.getValue().equals(action)) {
        return String.valueOf(entry.getKey());
      }
    }
    return null;
  }

  // Support for extra fields.
  //
  //	<select name=tossid><option value=7375>actual tapas  (5 casualties)</option></select>
  //	Coordinates: <input name=word type=text size=15 maxlength=7><br>(a valid set of coordinates is
  // 7 letters)<p>
  //
  // checkboxes (no examples)
  // radio buttons (no examples

  // <select name=tossid>><option value=7375>actual tapas  (5 casualties)</option>
  private static final Pattern SELECT_PATTERN =
      Pattern.compile("<select .*?name=['\"]?(\\w*)['\"]?.*?>(.*?)</select>", Pattern.DOTALL);
  private static final Pattern SELECT_OPTION_PATTERN =
      Pattern.compile("<option value=['\"]?(\\d*)['\"]?.*?>(.*?)</option>");

  public static Map<Integer, Map<String, Set<String>>> parseSelectInputs(
      final String responseText) {
    // Return a map from CHOICE => map from NAME => set of OPTIONS
    Map<Integer, Map<String, Set<String>>> rv = new TreeMap<>();

    if (responseText == null) {
      return rv;
    }

    // Find all choice forms
    Matcher m = FORM_PATTERN.matcher(responseText);
    while (m.find()) {
      String form = m.group();
      if (isNonChoiceForm(form)) continue;
      Matcher optMatcher = OPTION_PATTERN1.matcher(form);
      if (!optMatcher.find()) {
        continue;
      }

      // Collect all the selects from this form
      Map<String, Set<String>> choice = new TreeMap<>();

      // Find all "select" tags within this form
      Matcher s = SELECT_PATTERN.matcher(form);
      while (s.find()) {
        String name = s.group(1);

        // For each, extract all the options into a set
        Set<String> options = new TreeSet<>();

        Matcher o = SELECT_OPTION_PATTERN.matcher(s.group(2));
        while (o.find()) {
          options.add(o.group(1));
        }

        choice.put(name, options);
      }

      if (!choice.isEmpty()) {
        rv.put(Integer.parseInt(optMatcher.group(1)), choice);
      }
    }

    return rv;
  }

  public static Map<Integer, Map<String, Map<String, String>>> parseSelectInputsWithTags(
      final String responseText) {
    // Return a map from CHOICE => map from NAME => map from OPTION => SPOILER
    Map<Integer, Map<String, Map<String, String>>> rv = new TreeMap<>();

    if (responseText == null) {
      return rv;
    }

    // Find all choice forms
    Matcher m = FORM_PATTERN.matcher(responseText);
    while (m.find()) {
      String form = m.group();
      if (isNonChoiceForm(form)) continue;
      Matcher optMatcher = OPTION_PATTERN1.matcher(form);
      if (!optMatcher.find()) {
        continue;
      }

      // Collect all the selects from this form
      var choice = extractSelectInputChoices(form);

      if (!choice.isEmpty()) {
        rv.put(Integer.parseInt(optMatcher.group(1)), choice);
      }
    }

    return rv;
  }

  private static Map<String, Map<String, String>> extractSelectInputChoices(String form) {
    Map<String, Map<String, String>> choice = new TreeMap<>();

    // Find all "select" tags within this form
    var s = SELECT_PATTERN.matcher(form);
    while (s.find()) {
      var name = s.group(1);

      // For each, extract all the options into a map
      Map<String, String> options = new TreeMap<>();

      var o = SELECT_OPTION_PATTERN.matcher(s.group(2));
      while (o.find()) {
        var option = o.group(1);
        var tag = o.group(2);
        options.put(option, tag);
      }
      choice.put(name, options);
    }
    return choice;
  }

  // Coordinates: <input name=word type=text size=15 maxlength=7><br>(a valid set of coordinates is
  // 7 letters)<p>
  private static final Pattern INPUT_PATTERN = Pattern.compile("<input (.*?)>", Pattern.DOTALL);
  private static final Pattern NAME_PATTERN = Pattern.compile("name=['\"]?([^'\" >]+)['\"]?");
  private static final Pattern TYPE_PATTERN = Pattern.compile("type=['\"]?([^'\" >]+)['\"]?");

  public static Map<Integer, Set<String>> parseTextInputs(final String responseText) {
    // Return a map from CHOICE => set of NAME
    Map<Integer, Set<String>> rv = new TreeMap<>();

    if (responseText == null) {
      return rv;
    }

    // Find all choice forms
    Matcher m = FORM_PATTERN.matcher(responseText);
    while (m.find()) {
      String form = m.group();
      if (isNonChoiceForm(form)) continue;
      Matcher optMatcher = OPTION_PATTERN1.matcher(form);
      if (!optMatcher.find()) {
        continue;
      }

      // Collect all the text inputs from this form
      var choice = extractTextInputChoices(form);

      if (!choice.isEmpty()) {
        rv.put(Integer.parseInt(optMatcher.group(1)), choice);
      }
    }

    return rv;
  }

  private static Set<String> extractTextInputChoices(String form) {
    Set<String> choice = new TreeSet<>();

    // Find all "input" tags within this form
    var i = INPUT_PATTERN.matcher(form);
    while (i.find()) {
      var input = i.group(1);
      var t = TYPE_PATTERN.matcher(input);
      if (!t.find()) {
        continue;
      }

      var type = t.group(1);

      if (!type.equals("text")) {
        continue;
      }

      var n = NAME_PATTERN.matcher(input);
      if (!n.find()) {
        continue;
      }
      var name = n.group(1);
      choice.add(name);
    }

    return choice;
  }

  public static Map<Integer, Set<String>> parseHiddenInputs(final String responseText) {
    Map<Integer, Set<String>> hiddens = new TreeMap<>();

    if (responseText == null) {
      return hiddens;
    }

    // Find all choice forms
    Matcher m = FORM_PATTERN.matcher(responseText);
    while (m.find()) {
      String form = m.group();
      if (isNonChoiceForm(form)) continue;
      Matcher optMatcher = OPTION_PATTERN1.matcher(form);
      if (!optMatcher.find()) {
        continue;
      }

      // Collect all the hidden inputs from this form
      var extra = extractExtraHiddenFields(form);

      if (!extra.isEmpty()) {
        hiddens
            .computeIfAbsent(Integer.parseInt(optMatcher.group(1)), (k) -> new TreeSet<>())
            .addAll(extra);
      }
    }

    return hiddens;
  }

  private static final Set<String> STANDARD_HIDDEN_INPUT_FIELDS =
      Set.of("option", "pwd", "whichchoice");

  private static Set<String> extractExtraHiddenFields(String form) {
    Set<String> choice = new TreeSet<>();

    // Find all hidden "input" tags that are non-standard within this form
    var i = INPUT_PATTERN.matcher(form);
    while (i.find()) {
      var typeMatcher = TYPE_PATTERN.matcher(i.group(1));
      if (!typeMatcher.find()) {
        continue;
      }

      if (!typeMatcher.group(1).equals("hidden")) {
        continue;
      }

      var input = i.group(1);
      var n = NAME_PATTERN.matcher(input);
      if (!n.find()) {
        continue;
      }
      var name = n.group(1);

      if (STANDARD_HIDDEN_INPUT_FIELDS.contains(name)) {
        continue;
      }

      choice.add(name);
    }

    return choice;
  }

  public static String validateChoiceFields(
      final String decision, final String extraFields, final String responseText) {
    // Given the response text from visiting a choice, determine if
    // a particular decision (option) and set of extra fields are valid.
    //
    // Some decisions are not always available.
    // Some decisions have extra fields from "select" inputs which must be specified.
    // Some select inputs are variable: available options vary.
    // Some decisions have extra fields from "text" inputs which must be specified.

    // This method checks all of the following:
    //
    // - The decision is currently available
    // - All required select and inputs are supplied
    // - No invalid select values are supplied
    //
    // If all is well, null is returned, and decision + extraFields
    // will work as a response to the choice as presented
    //
    // If there are errors, returns a string, suitable as an error
    // message, describing all of the issues.

    // Must have a response text to examine
    if (responseText == null) {
      return "No response text.";
    }

    // Figure out which choice we are in from responseText
    int choice = ChoiceUtilities.extractChoice(responseText);
    if (choice == 0) {
      return "No choice adventure in response text.";
    }

    String choiceOption = choice + "/" + decision;

    // See if supplied decision is available
    Map<Integer, String> choices = ChoiceUtilities.parseChoices(responseText);
    if (!choices.containsKey(StringUtilities.parseInt(decision))) {
      return "Choice option " + choiceOption + " is not available.";
    }

    // Accumulate multiple errors in a buffer
    StringBuilder errors = new StringBuilder();

    // Extract supplied extra fields
    Set<String> extras = new TreeSet<>();
    for (String field : extraFields.split("&")) {
      if (field.isEmpty()) {
        // Ignore
      } else if (field.contains("=")) {
        extras.add(field);
      } else {
        errors.append("Invalid extra field: '").append(field).append("'; no value supplied.\n");
      }
    }

    // Selects: get a map from CHOICE => map from NAME => set of OPTIONS
    var formSelects = ChoiceUtilities.parseSelectInputs(responseText);

    // Texts: get a map from CHOICE => set of NAMES
    var formTexts = ChoiceUtilities.parseTextInputs(responseText);

    // Hidden overloads: get a map from CHOICE => set of NAMES
    var formHiddens = ChoiceUtilities.parseHiddenInputs(responseText);

    // Does the decision have extra select or text inputs?
    var key = StringUtilities.parseInt(decision);
    var selects = formSelects.get(key);
    var texts = formTexts.get(key);
    var hiddens = formHiddens.get(key);

    if (selects == null && texts == null && hiddens == null) {
      // No. If the user supplied no extra fields, all is well
      if (extras.isEmpty()) {
        return (!errors.isEmpty()) ? errors.toString() : null;
      }
      // Otherwise, list all unexpected extra fields
      for (String extra : extras) {
        errors
            .append("Choice option ")
            .append(choiceOption)
            .append(" does not require '")
            .append(extra)
            .append("'.\n");
      }
      return errors.toString();
    }

    // There are select and/or text inputs available/required for this form.

    // Make a map from supplied field => value
    Map<String, String> suppliedFields = new TreeMap<>();
    for (String field : extras) {
      // We validated this above; only fields with '=' are included
      int equals = field.indexOf("=");
      String name = field.substring(0, equals);
      String value = field.substring(equals + 1);
      suppliedFields.put(name, value);
    }

    // All selects in the form must have a value supplied
    if (selects != null) {
      for (Map.Entry<String, Set<String>> select : selects.entrySet()) {
        String name = select.getKey();
        Set<String> values = select.getValue();
        String supplied = suppliedFields.get(name);
        if (supplied == null) {
          // Did not supply a value for a field
          errors
              .append("Choice option ")
              .append(choiceOption)
              .append(" requires '")
              .append(name)
              .append("' but not supplied.\n");
        } else if (!values.contains(supplied)) {
          errors
              .append("Choice option ")
              .append(choiceOption)
              .append(" requires '")
              .append(name)
              .append("' but '")
              .append(supplied)
              .append("' is not a valid value.\n");
        } else {
          suppliedFields.remove(name);
        }
      }
    }

    // All text inputs in the form must have a value supplied
    if (texts != null) {
      for (String name : texts) {
        String supplied = suppliedFields.get(name);
        if (supplied == null) {
          // Did not supply a value for a field
          errors
              .append("Choice option ")
              .append(choiceOption)
              .append(" requires '")
              .append(name)
              .append("' but not supplied.\n");
        } else {
          suppliedFields.remove(name);
        }
      }
    }

    // All extra hidden inputs in the form must have a value supplied
    if (hiddens != null) {
      for (String name : hiddens) {
        String supplied = suppliedFields.get(name);
        if (supplied == null) {
          // Did not supply a value for a field
          errors
              .append("Choice option ")
              .append(choiceOption)
              .append(" requires '")
              .append(name)
              .append("' but not supplied.\n");
        } else {
          suppliedFields.remove(name);
        }
      }
    }

    // No unnecessary fields in the form can be supplied
    for (Map.Entry<String, String> supplied : suppliedFields.entrySet()) {
      String name = supplied.getKey();
      errors
          .append("Choice option ")
          .append(choiceOption)
          .append(" does not require '")
          .append(name)
          .append("'.\n");
    }

    return (!errors.isEmpty()) ? errors.toString() : null;
  }

  public static void printChoices(final String responseText) {
    Map<Integer, String> choices = ChoiceUtilities.parseChoicesWithSpoilers(responseText);
    Map<Integer, Map<String, Map<String, String>>> selects =
        ChoiceUtilities.parseSelectInputsWithTags(responseText);
    Map<Integer, Set<String>> texts = ChoiceUtilities.parseTextInputs(responseText);
    for (Map.Entry<Integer, String> choice : choices.entrySet()) {
      Integer choiceKey = choice.getKey();
      RequestLogger.printHtml(
          "<b>choice "
              + choiceKey
              + "</b>: "
              + StringUtilities.getEntityEncode(choice.getValue(), false));
      Map<String, Map<String, String>> choiceSelects = selects.get(choiceKey);
      if (choiceSelects != null) {
        for (Map.Entry<String, Map<String, String>> select : choiceSelects.entrySet()) {
          Map<String, String> options = select.getValue();
          RequestLogger.printHtml(
              "\u00A0\u00A0select = <b>"
                  + select.getKey()
                  + "</b> ("
                  + options.size()
                  + " options)");
          for (Map.Entry<String, String> option : options.entrySet()) {
            RequestLogger.printHtml(
                "\u00A0\u00A0\u00A0\u00A0" + option.getKey() + " => " + option.getValue());
          }
        }
      }
      Set<String> choiceTexts = texts.get(choiceKey);
      if (choiceTexts != null) {
        for (String name : choiceTexts) {
          RequestLogger.printHtml("\u00A0\u00A0text = <b>" + name + "</b>");
        }
      }
    }
  }
}
