package com.donohoedigital.ddphotos.config;

import java.util.Objects;

public class HeroEntry {
    private String image;
    private String base;
    private String crop;

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public String getBase() { return base; }
    public void setBase(String base) { this.base = base; }

    public String getCrop() { return crop; }
    public void setCrop(String crop) { this.crop = crop; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HeroEntry h)) return false;
        return Objects.equals(image, h.image)
            && Objects.equals(base,  h.base)
            && Objects.equals(crop,  h.crop);
    }

    @Override
    public int hashCode() {
        return Objects.hash(image, base, crop);
    }
}
