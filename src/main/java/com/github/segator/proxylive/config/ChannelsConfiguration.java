package com.github.segator.proxylive.config;

import lombok.Data;
import lombok.Value;

@Data
public class ChannelsConfiguration {
    GitSource git;
    String url;
    long refresh;
    String type;



}
