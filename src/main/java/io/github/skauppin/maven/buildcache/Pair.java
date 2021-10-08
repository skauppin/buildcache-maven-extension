package io.github.skauppin.maven.buildcache;

public class Pair<T> {
  private T a;
  private T b;

  public Pair(T a, T b) {
    this.a = a;
    this.b = b;
  }

  public T getA() {
    return a;
  }

  public T getB() {
    return b;
  }
}
