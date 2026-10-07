package it.bollettalab.platform;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;

@MappedSuperclass
public abstract class ScopedEntity {
  @TenantId
  @Column(name = "workspace_id", nullable = false, length = 36)
  private String workspaceId;

  @Column(name = "author_id", length = 36)
  private String authorId;

  @Column(name = "author_name", length = 200)
  private String authorName;

  @Column(name = "company_name", length = 200)
  private String companyName;

  @Column(name = "company_vat", length = 20)
  private String companyVat;

  @Column(name = "company_address", length = 300)
  private String companyAddress;

  @PrePersist
  protected void attribution() {
    Identity i = RequestContext.identity();
    if (i != null) {
      authorId = i.id();
      authorName = i.name();
      companyName = i.companyName();
      companyVat = i.vatNumber();
      companyAddress = i.address();
    }
  }

  public String getCompanyVat() {
    return companyVat;
  }

  public String getCompanyAddress() {
    return companyAddress;
  }

  public String getAuthorId() {
    return authorId;
  }

  public String getAuthorName() {
    return authorName;
  }

  public String getCompanyName() {
    return companyName;
  }

  public String getWorkspaceId() {
    return workspaceId;
  }
}
