package com.payangar.encounters.config;

public class WeightedMob {

    public String id = "";
    public int weight = 1;
    public String nbt = null;
    public String label = null;

    public WeightedMob() {}

    public WeightedMob(String id, int weight) {
        this.id = id;
        this.weight = weight;
    }

    public WeightedMob(String id, int weight, String nbt, String label) {
        this.id = id;
        this.weight = weight;
        this.nbt = nbt;
        this.label = label;
    }
}
