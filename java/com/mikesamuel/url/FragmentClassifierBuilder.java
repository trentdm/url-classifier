package com.mikesamuel.url;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

/**
 * Builds a classifier over the fragment content excluding the '#'.
 * An absent fragment is equivalent to the empty fragment per RFC 3986.
 */
public final class FragmentClassifierBuilder {
  private URLClassifier asRelativeUrlPred;
  private Predicate<? super Optional<String>> fragmentPred;

  static final URLClassifier MATCH_NO_URLS = URLClassifier.or();

  private FragmentClassifierBuilder() {
    // Use static factory
  }

  /** A new blank builder. */
  public static FragmentClassifierBuilder builder() {
    return new FragmentClassifierBuilder();
  }

  /**
   * Builds a classifier based on previous allow/match decisions.
   * This may be reused after a call to build and subsequent calls to
   * allow/match methods will not affect previously built classifiers.
   */
  public FragmentClassifier build() {
    Predicate<? super Optional<String>> fragmentClassifier = this.fragmentPred;
    URLClassifier asRelativeUrlClassifier = this.asRelativeUrlPred;
    if (fragmentClassifier == null) {
      fragmentClassifier = Predicates.alwaysFalse();
    }
    if (asRelativeUrlClassifier == null) {
      asRelativeUrlClassifier = MATCH_NO_URLS;
    }
    return new FragmentClassifierImpl(fragmentClassifier, asRelativeUrlClassifier);
  }

  /**
   * If there is no fragment then p will receive the absent optional.
   * If there is a fragment then p will receive it with the leading "#".
   * RFC 3986 says
   * <blockquote>
   *    two URIs that differ only by the suffix "#" are considered
   *    different regardless of the scheme.
   * </blockquote>
   */
  public FragmentClassifierBuilder matches(Predicate<? super Optional<String>> p) {
    this.fragmentPred = this.fragmentPred == null
        ? p
        : Predicates.or(this.fragmentPred, p);
    return this;
  }

  /**
   * It's not uncommon for single-page applications to embed a path in a query.
   * URLs will match when there is a fragment whose content (sans "#") parses
   * as a relative URL that matches p in the context of
   * http://[special-unkown-host]/
   *
   * <p>By "relative," we mean not absolute per RFC 3986.  An absolute path is
   * still a relatvie URL by this scheme since it specifies no scheme.
   *
   * <p>This requires that the fragment will be present, so to allow
   * no fragment OR the fragments described above, use FragmentClassifier.or(...).
   */
  public FragmentClassifierBuilder matchFragmentAsIfRelativeURL(URLClassifier p) {
    this.asRelativeUrlPred = this.asRelativeUrlPred == null
        ? p
        : URLClassifier.or(this.asRelativeUrlPred, p);
    return this;
  }
}

final class FragmentClassifierImpl implements FragmentClassifier {
  final Predicate<? super Optional<String>> fragmentClassifier;
  final URLClassifier asRelativeUrlClassifier;

  FragmentClassifierImpl(
      Predicate<? super Optional<String>> fragmentClassifier,
      URLClassifier asRelativeUrlClassifier) {
    this.fragmentClassifier = Preconditions.checkNotNull(fragmentClassifier);
    this.asRelativeUrlClassifier = Preconditions.checkNotNull(asRelativeUrlClassifier);
  }

  @Override
  public Classification apply(URLValue x) {
    String fragment = x.getFragment();
    Classification result = Classification.NOT_A_MATCH;
    Optional<String> fragmentOpt = Optional.fromNullable(fragment);
    if (this.fragmentClassifier.apply(fragmentOpt)) {
      result = Classification.MATCH;
    }
    if (fragment != null
        && result == Classification.NOT_A_MATCH
        && !FragmentClassifierBuilder.MATCH_NO_URLS.equals(
            this.asRelativeUrlClassifier)) {
      URLValue fragmentUrl = URLValue.of(
          // Explicitly do not use x's path.
          URLContext.DEFAULT, fragment.substring(1));
      switch (this.asRelativeUrlClassifier.apply(fragmentUrl)) {
        case INVALID:
          return Classification.INVALID;
        case MATCH:
          result = Classification.MATCH;
          break;
        case NOT_A_MATCH:
          break;
      }
    }
    return result;
  }

}