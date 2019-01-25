# proxylive
This application allow you to transcode and broadcast on demand a http mpeg ts to transcoded mpegts/hls

you only need to define source http live streaming and start the application
after all

## Url Definition
you could get a broadcasted stream from
http://{server}:8080/view/{profile}/{id}

If you want the full channel list
http://[server]:8080/channel/list/[format]/[profile]
in case of auth enabled
http://[server]:8080/channel/list/[format]/[profile]?user=clientUser&pass=clientPass

if you want to load EPG(now only is extracted from tvheadend)
http://[server]:8080/epg

#### server
ip or domain of the host where the application is running

#### profile
This is the defined profile in the yaml configuration, is a alias of a ffmpeg video encoding configuration.
By default we have 1080p, 720p, raw, aac profiles, you can add or modify as your needs.

#### id
Source Stream identification.
This ID is used to identify streams and connect to the source stream channel.
for example in tvheadend http://tvheadend:9981/stream/channel/{id}

#### format
The format the server will stream the live streaming.
mpeg: mpegts stream
hls: hls stream
if you don't know what to choose, use mpeg

## Requirements
- jre8 or docker > 1.9
- Http Live Streaming Source

## Configuration /app/application.yml
```yml
source:
    #My Tvheadend source instance
    tvheadendurl: http://tvheadend:9981/stream/channel/{id}
    #In case of source connection problem, retry connection in 10 seconds
    reconnectTimeout: 10
streamTimeout: 60 
#Client Stream timeout, if no bytes from backend on this timeout range, the client connection will be closed
internalConnection: true
#all tasks always try to connect to  proxylive(loop) instead of direct to tvheadend.
#Pros:
#-Single connection to tvheadend per channel
#-when 2 clients with HLS and mpegTS with the same transcoding profile only transcoded one time.(so less cpu usage)
#Cons:
#-Slower start when the channel is not yet initialized on HLS/or mpegTs with transcoding.
ffmpeg:
    path: '/usr/bin/ffmpeg'
    profiles:
        -
            alias: "aac"
            parameters: "-sn -c:a:0 aac -b:a 320k -c:v copy"
        -
            alias: "360p"
            parameters: "-sn -c:a:0 aac -b:a 128k -c:v libx264 -tune zerolatency -g 10 -vprofile high -level 3.0 -crf 18 -movflags +faststart -bufsize 15000k -maxrate 700k  -preset veryfast -vf scale=-1:360,yadif=0"
        -
            alias: "480p"
            parameters: "-sn -c:a:0 aac -b:a 196k -c:v libx264 -tune zerolatency -g 10 -vprofile high -level 3.0 -crf 18 -movflags +faststart -bufsize 15000k -maxrate 1500k  -preset veryfast -vf scale=-1:480,yadif=0"
        -
            alias: "720p"
            parameters: "-sn  -c:a:0 aac -b:a 320k -c:v libx264 -tune zerolatency -g 10 -vprofile high -level 4.0 -crf 18 -movflags +faststart -bufsize 15000k -maxrate 3000k  -preset veryfast -vf scale=-1:720,yadif=0"
        -
            alias: "1080p"
            parameters:  "-sn  -c:a:0 aac -b:a 320k -c:v libx264 -tune zerolatency -g 300 -vprofile high -level 4.0 -movflags +faststart -bufsize 15000k -maxrate 5000k  -preset veryfast -vf yadif=0"
    mpegTS:
        parameters: "-threads 0 -f mpegts -mpegts_m2ts_mode 1 -mpegts_copyts 1 -mpegts_flags +resend_headers  "
		#Specific parameters for mpegTS(only works if a diferent profile than raw is selected, raw send direct data from backend so no transcoded, in this case not applied this parameter.
    hls:
        tempPath: "/tmp"
		#Path where HLS will save segment files and playlist
        parameters: "-flags -global_header  -avoid_negative_ts disabled -map_metadata -1 -start_at_zero -copyts -flags -global_header -vsync cfr -y -nostats -f hls  -hls_time 2 -hls_list_size 10 -hls_wrap 20 -hls_allow_cache 0  -hls_flags +append_list -hls_flags +discont_start -hls_flags +delete_segments"
		#FFmpeg parameters specific for HLS		
        timeout: 120
		#When user disconnect from stream, we doesnt have any way to be sure the user is totally disconnected so this parameter control how many seconds since last download of a segment of a concret stream, if the timeout is reached, the timeout is canceled.
        
buffers:
    #The max amount of bytes the application can read from a stream, default 1MB
    chunkSize: 1048576
    #Buffer size of the broadcast buffer,larger buffer more stable but larger delay than source stream, default 50MB
    broadcastBufferSize: 52428800
#Override the endpoint in the playlist(optional)
endpoint: https://myendpoint.com
```
You can use plex authentication or ldap, adding this in the application.yml
all your plex friends that have the option "allow channels" will have access to stream from proxylive
```yml
authentication:
    #For plex auth
    plex:
        adminUser: "plexOwnerUser"
        adminPass: "plexOwnerPass"
        serverName: "MyPlexServerName"
    
    #For LDAP auth (not implemented yet)    
    ldap:
        server: "ldap://server:389"
        username: "user"
        pass: "pass"
        searchBase: "dc=ad,dc=my-domain,dc=com"
```


Client users will connect attaching to the URL  ?user=user&pass=pass
```
http://localhost:8080/channel/list/mpeg/1080p?user=myplexuser&pass=myplexpass
```

### Load Configuration from consul
You will need to create a file bootstrap.yml with this format
```yml
spring:
    application:
        name: proxylive
    cloud:
        consul:
            host: consulip
            port: consulport
```
then you don't need to override application.yml, all the parameters will be loaded from consul
go to Key/Value 
and create the parameters with next format
```
config/proxylive/my/key/path
#Example
config/proxylive/source/tvheadendurl = http://tvhUser:tvhPass@mytvhServer:9981/
```
## Run in docker
```bash
docker run -d -v /my/application.yml:/app/application.yml segator/proxylive
```
## RoadMap
- [ ] Multiple Source Input(Right now we only can work with a single source
- [ ] RTMP Output(I not sure if I'm going to implement it because you can mix this app with nginx to have this feature)
- [ ] Dash Output(I not sure if I'm going to implement it because you can mix this app with nginx to have this feature)
- [X] HLS Output(implemented but not tested)
- [x] MPEG-TS Output
- [X] Reconnect Source/transcoder on failure without disconnect clients,
- [X] Plex Authentication
- [ ] LDAP Authentication
- [X] Playlist Generator
- [X] EPG extractor
- [ ] Refactor(This application is a Prove of concept, the code is not clean enough and aren't tested to use in a production environment
