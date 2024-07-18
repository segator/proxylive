package com.github.segator.proxylive.controller;

import com.github.segator.proxylive.ProxyLiveUtils;
import com.github.segator.proxylive.config.ProxyLiveConfiguration;
import com.github.segator.proxylive.entity.ApiChannel;
import com.github.segator.proxylive.entity.Channel;
import com.github.segator.proxylive.service.AuthenticationService;
import com.github.segator.proxylive.service.ChannelService;
import com.github.segator.proxylive.service.EPGService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/api")
public class FrontendController {
    private final Logger logger = LoggerFactory.getLogger(FrontendController.class);

    private final ApplicationContext context;
    private final ProxyLiveConfiguration config;
    private final AuthenticationService authService;
    private final ChannelService channelService;
    private final EPGService epgService;

    public FrontendController(ApplicationContext context, ProxyLiveConfiguration config, AuthenticationService authService, ChannelService channelService, EPGService epgService) {
        this.context = context;
        this.config = config;
        this.authService = authService;
        this.channelService = channelService;
        this.epgService = epgService;
    }

    @RequestMapping(value = "channels", method = RequestMethod.GET)
    public @ResponseBody
    List<ApiChannel> generatePlaylist(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        List<ApiChannel> ApiChannelList=new ArrayList<>();
        String requestBaseURL = ProxyLiveUtils.getBaseURL(request);

        for (Channel channel : channelService.getChannelList()) {
            ApiChannel apiChannel = ApiChannel.Create(channel);
            apiChannel.setChannelURL(String.format("%s/view/%s/%s/playlist.m3u8?token=%s",requestBaseURL, "1080p", channel.getId(),authentication.getCredentials()));
            if(channel.getLogoURL()!=null || channel.getLogoFile()!=null) {
                apiChannel.setLogoURL(String.format("%s/channel/%s/icon", requestBaseURL, channel.getId()));
            }
            ApiChannelList.add(apiChannel);
        }
        List<ApiChannel> channelsOrdered = new ArrayList(ApiChannelList);
        channelsOrdered.sort(new Comparator<ApiChannel>() {
            @Override
            public int compare(ApiChannel o1, ApiChannel o2) {
                return o1.getNumber().compareTo(o2.getNumber());
            }
        });
        return channelsOrdered;
    }
    @RequestMapping(value = "channel/{channelID}", method = RequestMethod.GET)
    public @ResponseBody
    ApiChannel getChannel(HttpServletRequest request, HttpServletResponse response,@PathVariable("profile") String channelID) {
        Channel channel = channelService.getChannelList().stream().filter(f->f.getId().equals(channelID)).findAny().get();
        ApiChannel apiChannel = ApiChannel.Create(channel);
        if(channel.getEpgID()!=null){
            apiChannel.setEpgProgramList(epgService.getEPGFromChannelID(channel.getEpgID()));
        }
        return apiChannel;
    }
}
