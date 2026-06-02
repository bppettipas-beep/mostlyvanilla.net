package com.mostlyvanilla.chatfilter;

import java.util.List;
import java.util.regex.Pattern;

public class WordCategory {

    public final String name;
    private final List<Pattern> pass1Patterns; // applied to leet-normalized text (spaces kept)
    private final List<String>  pass2Words;    // substring check on fully-normalized text

    private List<String> actions = List.of("warn");
    private boolean enabled = true;

    public WordCategory(String name, List<Pattern> pass1Patterns, List<String> pass2Words) {
        this.name          = name;
        this.pass1Patterns = pass1Patterns;
        this.pass2Words    = pass2Words;
    }

    /** Returns true if either the leet-normalized or fully-normalized text triggers this category. */
    public boolean matches(String pass1Text, String pass2Text) {
        if (!enabled) return false;
        for (Pattern p : pass1Patterns) {
            if (p.matcher(pass1Text).find()) return true;
        }
        for (String word : pass2Words) {
            if (pass2Text.contains(word)) return true;
        }
        return false;
    }

    public List<String> getActions()                  { return actions;          }
    public void         setActions(List<String> a)    { this.actions = a;        }
    public boolean      isEnabled()                   { return enabled;          }
    public void         setEnabled(boolean enabled)   { this.enabled = enabled;  }
}
