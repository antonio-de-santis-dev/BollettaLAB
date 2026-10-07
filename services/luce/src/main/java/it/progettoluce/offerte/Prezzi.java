package it.progettoluce.offerte;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

@Embeddable
public class Prezzi {
  @PositiveOrZero
  @Digits(integer = 8, fraction = 8)
  @Column(precision = 16, scale = 8)
  private BigDecimal f0;

  @PositiveOrZero
  @Digits(integer = 8, fraction = 8)
  @Column(precision = 16, scale = 8)
  private BigDecimal f1;

  @PositiveOrZero
  @Digits(integer = 8, fraction = 8)
  @Column(precision = 16, scale = 8)
  private BigDecimal f23;

  @PositiveOrZero
  @Digits(integer = 8, fraction = 8)
  @Column(precision = 16, scale = 8)
  private BigDecimal f2;

  @PositiveOrZero
  @Digits(integer = 8, fraction = 8)
  @Column(precision = 16, scale = 8)
  private BigDecimal f3;

  public Prezzi() {}

  public Prezzi(BigDecimal f0, BigDecimal f1, BigDecimal f23, BigDecimal f2, BigDecimal f3) {
    this.f0 = f0;
    this.f1 = f1;
    this.f23 = f23;
    this.f2 = f2;
    this.f3 = f3;
  }

  public BigDecimal getF0() {
    return f0;
  }

  public BigDecimal getF1() {
    return f1;
  }

  public BigDecimal getF23() {
    return f23;
  }

  public BigDecimal getF2() {
    return f2;
  }

  public BigDecimal getF3() {
    return f3;
  }

  public void setF0(BigDecimal v) {
    f0 = v;
  }

  public void setF1(BigDecimal v) {
    f1 = v;
  }

  public void setF23(BigDecimal v) {
    f23 = v;
  }

  public void setF2(BigDecimal v) {
    f2 = v;
  }

  public void setF3(BigDecimal v) {
    f3 = v;
  }

  public BigDecimal valore(Fascia fascia) {
    return switch (fascia) {
      case F0 -> f0;
      case F1 -> f1;
      case F23 -> f23;
      case F2 -> f2;
      case F3 -> f3;
    };
  }
}
