# proxylive
This application allow you to transcode and broadcast on demand a http mpeg ts to transcoded mpegts/hls

you only need to define source http live streaming and start the application
after all

##Url Definition
you could get a transcoded stream from
http://{server}:8080/view/{profile}/{id}
#### server
ip or domain of the host where the application is running

#### profile
This is the defined profile in the yaml configuration, is a alias of a ffmpeg video encoding configuration.
By default we have 1080p, 720p, raw, aac profiles, you can add or modify as your needs.

#### id
Source Stream identification.
This ID is used to identify streams and connect to the source stream channel.
for example in tvheadend http://tvheadend:9981/stream/channel/{id}

## Requirements
- jre8 or docker > 1.9
- Http Live Streaming Source

## Configuration
```yml
source:
    #My Tvheadend source instance
    url: http://tvheadend:9981/stream/channel/{id}
    #In case of source connection problem, retry connection in 10 seconds
    reconnectTimeout: 10
ffmpeg:
    path: '/usr/bin/ffmpeg'
    profiles:
        -
            alias: "aac"
            parameters: "-c:a:0 aac -b:a 320k -c:v copy -threads 0 -f mpegts"
        -
            alias: "raw"
            parameters: "-c:a copy -c:v copy -threads 0 -f mpegts"
        -
            alias: "720p"
            parameters: "-c:a:0 aac -b:a 320k -c:v libx264 -tune zerolatency -g 10 -vprofile high -level 4.0 -crf 18 -movflags +faststart -bufsize 15000k -maxrate 10000k  -preset fast -vf \"scale=-1:720,yadif=0\" -threads 0 -f mpegts"
        -
            alias: "1080p"
            parameters:  "-c:a:0 aac -b:a 320k -c:v libx264 -tune zerolatency -g 10 -vprofile high -level 4.1 -crf 21 -movflags +faststart -bufsize 15000k -maxrate 10000k  -preset fast -vf yadif=0 -threads 0 -f mpegts"
    
    hls:
        tempPath: "/tmp"
        parameters: "-vcodec copy -acodec copy   -flags -global_header  -avoid_negative_ts disabled -map_metadata -1 -start_at_zero -copyts -flags -global_header -vsync cfr -y -nostats -f hls -hls_time 1 -hls_list_size 20 -hls_wrap 30"
        timeout: 120
        
buffers:
    #The max amount of bytes the application can read from a stream, default 1MB
    chunkSize: 1048576
    #Buffer size of the broadcast buffer,larger buffer more stable but larger delay than source stream, default 50MB
    broadcastBufferSize: 52428800
```

## Run in docker
```bash
docker run -d -v /my/application.yml:/app/application.yml segator/proxylive
```
## RoadMap
- [ ] RTMP Input
- [ ] HLS Input
- [ ] Dash Input
- [ ] RTMP Output(I not sure if I'm going to implement it because you can mix this app with nginx to have this feature)
- [ ] Dash Output(I not sure if I'm going to implement it because you can mix this app with nginx to have this feature)
- [ ] HLS Output(I not sure if I'm going to implement it because you can mix this app with nginx to have this feature)
- [x] MPEG-TS
- [ ] Refactor(This application is a Prove of concept, the code is not clean enough and aren't tested to use in a production environment
