# proxylive
This application allow you to transcode and broadcast on demand a http mpeg ts to transcoded mpegts/hls


### Features
- **broadcasting:** Multiple users conected to same channel only consume a single connection to your backend.
- **transcode on demand:** you can define transcode profiles(ffmpeg) that allow you to convert the format to whatever you want. (HLS & mpegTS containers)
- **M3u Playlist:** proxylive generate on real time m3u playlist
- **Authentication:** Plex Auth and LDAP Auth, Only accept users you want.
- **Multiple sources per channel:** If a source goes down automatically get another source without close user connection or stop transcoding.
- **EPG Support**

### Types of channels sources:
- **Tvheadend:** Will load all the channels from tvheadend
- **Custom JSON:** You will need to define a Json with all the channel info


#### Tvheadend Channel Sources:
application.yaml
```yaml
source:
    tvheadendURL: http://user:password@your.tvheadend.com:9981
    channels:
        type: tvheadend
        refresh: 60 #In seconds
```
#### Custom JSON Channel Source
Custom JSON can be read from:
- **File/URL:** every X seconds the file will be readed
 
application.yaml
```yaml
source:
    channels:
        type: json
        refresh: 60 #In seconds
        url: file:///my/disk/channels.json or  http://my.server.com/channels.json
```
- **Git:** Channel and picons will be readed from a Git repository, only refreshed if commits detected.

application.yaml
```yaml
source:
    channels:
        git:
            repository: https://github.com/user/repo.git
            #optional
            user: youruser
            #optional
            pass: yourpass 
            branch: master
        type: json
        refresh: 60 #In seconds
```




channels.json
```json
[ 
    {
      "number" : 1,
      "name" : "La 1 HD",
      "id" : "la1hd",
      "epgID" : "La 1 HD",
      "logoFile":"/picons/la1hd.png",
      "logoURL": "http://server.com/logo.png",
      "categories" : [ "Generales", "TDT" ],
      "ffmpegParameters": "extra ffmpeg parameters only for this channel, this is optional, usable for MTPS extracting",
      "sources" : [ 
          {
            "url" : "http://your/server/stream",
            "priority" : 1,
            "type" : "ffmpeg" <- This is used to force ffmpeg usage when raw profile (usefull for MTPS)
          },
          {
            "url" : "udp://225.225.201.1:8008",
            "priority" : 2
          },
          {
            "url" : "rtp://200.100.1500:1001",
            "priority" : 3
          },
          {
            "url" : "hlss://my.server/my/stream.m3u8", <- hls or hlss(TLS)
            "priority" : 4
          },
          {
            "url" : "dashs://my.server/my/stream.m3u8",  <- dash or dashs(TLS)
            "priority" : 5
          },
          {
            "url" : "rtmp://my.server/rtmp/live",
            "priority" : 6
          }  
      ]
      
    }
]
```
Picons can be readed from URL or File (logoFile or logoURL), logoURL will be cached on proxylive,
so clients will request picons to proxylive instead of directly to the defined URL.
In case of use git backend, picons will be expected to be in the root of the git repository.







### Configuration
You need to write application.yml file in the same directory where jar is running (In case of docker on /app/application.yaml)

application.yaml
```yaml
source:
    #My Tvheadend source instance
            
    #In case of source connection problem, retry connection in 10 seconds
    reconnectTimeout: 10
    ##This is optional, only used if tvheadend channel backend
    tvheadendURL: http://tvheadend:9981
    epg:
        url: http://tvheadend:9981/xmltv/channels
        refresh: 600 #10 minutes
    channels:
        #Check (types of channels sources) for more information of this section


streamTimeout: 60
#Client Stream timeout, if no bytes from backend on this timeout range, the client connection will be closed
#Pros:
#-Single connection to tvheadend per channel
#-when 2 clients with HLS and mpegTS with the same transcoding profile only transcoded one time.(so less cpu usage)
#Cons:
#-Slower start when the channel is not yet initialized on HLS/or mpegTs with transcoding.
ffmpeg:
    #Path where proxylive can find ffmpeg binary
    path: '/usr/bin/ffmpeg'
    
    ##Profiles definition alias param is used in the view link, see (Url Definition) for more details of use profiles
    profiles:
        -
            alias: "aac"
            #FFmpeg parameters, check ffmpeg documentation for more info
            parameters: "-i {input} {channelParameters} -sn -ac 2 -c:a aac -b:a 320k -c:v copy"
        -
            alias: "240p"
            parameters: "-i {input} {channelParameters} -sn -c:a:0 aac -ac 2 -b:a 64k -c:v libx264 -tune zerolatency -g 10 -vprofile high -level 4.0 -crf 18 -movflags +faststart -bufsize 15000k -maxrate 300k  -preset veryslow -vf scale=-1:244,yadif=0"
        -
            alias: "360p"
            parameters: "-i {input} {channelParameters} -sn -c:a:0 aac -ac 2 -b:a 96k -c:v libx264 -tune zerolatency -g 10 -vprofile high -level 4.0 -crf 18 -movflags +faststart -bufsize 15000k -maxrate 700k  -preset veryslow -vf scale=-1:360,yadif=0"
        -
            alias: "480p"
            parameters: "-i {input} {channelParameters} -sn -c:a:0 aac -ac 2 -b:a 196k -c:v libx264 -tune zerolatency -g 10 -vprofile high -level 4.0 -crf 18 -movflags +faststart -bufsize 15000k -maxrate 1500k  -preset slow -vf scale=-1:484,yadif=0"
        -
            alias: "720p"
            parameters: "-i {input} {channelParameters} -sn  -c:a:0 aac -ac 2 -b:a 320k -c:v libx264 -tune zerolatency -g 10 -vprofile high -level 4.0 -crf 18 -movflags +faststart -bufsize 15000k -maxrate 3000k  -preset fast -vf scale=-1:720,yadif=0"
        -
            alias: "1080p"
            parameters:  "-i {input} {channelParameters} -sn  -c:a:0 aac -ac 2 -b:a 320k -c:v libx264 -tune zerolatency -g 300 -vprofile high -level 4.0 -movflags +faststart -bufsize 15000k -maxrate 5000k  -preset faster -vf yadif=0"
    mpegTS:
        parameters: "-threads 0 -f mpegts -mpegts_m2ts_mode 1 -mpegts_copyts 1 -mpegts_flags +resend_headers  "
		#Specific parameters for mpegTS(only works if a diferent profile than raw is selected, raw send direct data from backend so no transcoded, in this case not applied this parameter.
    hls:
        #by default HLS is disabled 
        enabled: false
        tempPath: "/tmp"
		#Path where HLS will save segment files and playlist
        parameters: "-flags -global_header  -avoid_negative_ts disabled -map_metadata -1 -start_at_zero -copyts -flags -global_header -vsync cfr -y -nostats -f hls  -hls_time 2 -hls_list_size 10 -hls_wrap 20 -hls_allow_cache 0  -hls_flags +append_list -hls_flags +discont_start -hls_flags +delete_segments"
		#FFmpeg parameters specific for HLS
        timeout: 30
		#When user disconnect from stream, we doesnt have any way to be sure the user is totally disconnected so this parameter control how many seconds since last download of a segment of a concret stream, if the timeout is reached, the timeout is canceled.

buffers:
    #The max amount of bytes the application can read from a stream, default 1MB in a single action
    chunkSize: 1048576
    #Buffer size of the broadcast buffer,larger buffer more stable but larger delay than source stream, default 50MB
    broadcastBufferSize: 52428800
    
    
### Authentication
```
You can use plex authentication or ldap, adding this in the application.yaml
all your plex friends that have the option "allow channels" will have access to stream from proxylive
```yaml
authentication:
    #For plex auth
    plex:
        #every refresh time in seconds we will fetch users that are allowed to login
        refresh:  10800
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


## Url Definition
you could get a broadcasted stream from
http://{server}:8080/view/{profile}/{channelID}

### get the playlist
http://[server]:8080/channel/list/[format]/[profile]

in case of auth enabled

http://[server]:8080/channel/list/[format]/[profile]?user=clientUser&pass=clientPass

### get EPG
http://[server]:8080/epg

#### server
ip or domain of the host where the application is running

#### profile
This is the defined profile in the yaml configuration, is a alias of a ffmpeg video encoding configuration.
By default we have 1080p, 720p, raw, aac profiles, you can add or modify as your needs.

#### id
Source Stream identification.
This ID is used to identify channels,
in case you use tvheadend backend then channelID is equal to streamID
for example in tvheadend http://tvheadend:9981/stream/channel/{id}

#### format
The format the server will stream the live streaming.
- mpeg: mpegts stream
- hls: hls stream


if you don't know what to choose, use mpeg

## Requirements
- jre8 or docker > 1.9
- Http Live Streaming Source
- ffmpeg installed

## Build
to build you need JDK > 1.9 and maven 3.5.2 installed.

then...
```bash
git clone https://github.com/segator/proxylive
cd proxylive
mvn clean install
#you will have a file called dist/proxylive.jar
```

## Run in docker
```bash
docker run --name=proxylive -p 8080:8080 --restart=always -d -v /my/application.yml:/app/application.yml:ro segator/proxylive
```

## Run on any OS
to run first you need to have at least java jre 1.9 installed.
```bash
##Remember to have the application.yml in the working directory before run the app
java -jar proxylive.jar
```


## RoadMap
- [X] Multiple Source Input(Failover sources)
- [X] TVHeadend Backend as source
- [X] Custom Backends as source
- [X] QSV/VAAPI Support (Hardware transcoding)
- [ ] NVENC Support (Hardware Nvidia Transcoding)
- [X] Prometheus Support
- [ ] Fluend support
- [X] UDP Input
- [X] RTMP Input
- [X] RTSP Input
- [X] HLS Input
- [X] DASH Input
- [ ] RTMP Output
- [ ] UDP Output
- [ ] Dash Output
- [X] HLS Output
- [x] MPEG-TS Output
- [X] Reconnect Source/transcoder on failure without disconnect clients,
- [X] Plex Authentication
- [ ] LDAP Authentication
- [X] Playlist Generator
- [X] EPG extractor
- [ ] Refactor(This application is a Prove of concept, the code is not clean enough and aren't tested to use in a production environment
