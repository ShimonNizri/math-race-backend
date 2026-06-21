package com.example.math_race.controller;

import com.example.math_race.dto.http.request.CreateRaceRequest;
import com.example.math_race.dto.http.request.JoinRaceRequest;
import com.example.math_race.dto.http.request.PublicRacesListRequest;
import com.example.math_race.dto.http.request.RequestMetadata;
import com.example.math_race.dto.http.ApiResponse;
import com.example.math_race.dto.http.response.CreateRaceResponse;
import com.example.math_race.dto.http.response.JoinRaceResponse;
import com.example.math_race.dto.http.response.RaceInfoResponse;
import com.example.math_race.dto.wsMessage.request.*;
import com.example.math_race.exception.ErrorCode;
import com.example.math_race.service.RaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/race")
public class RaceController {

    private final RaceService raceService;

    @Autowired
    public  RaceController(RaceService raceService) {
        this.raceService = raceService;
    }

    @MessageMapping("/race/{roomCode}/player/submit")
    public void handleSubmit(@DestinationVariable String roomCode, @Valid @Payload SubmitQuestionRequest request , StompHeaderAccessor accessor) {
        raceService.handleSubmitQuestion(roomCode,request, accessor);
    }

    @MessageMapping("/race/{roomCode}/player/junction/choose")
    public void handleJunctionChoose(@DestinationVariable String roomCode, @Valid @Payload JunctionChooseRequest request , StompHeaderAccessor accessor) {
        raceService.handleJunctionChoose(roomCode, request, accessor);
    }

    @MessageMapping("/race/{roomCode}/host/start")
    public void handleChangeRaceStatus(@DestinationVariable String roomCode, StompHeaderAccessor accessor) {
        raceService.handleStartRace(roomCode, accessor);
    }

    @MessageMapping("/race/{roomCode}/host/kick")
    public void handleKickPlayer(@DestinationVariable String roomCode, @Valid @Payload KickPlayerRequest request, StompHeaderAccessor accessor) {
        raceService.kickPlayerFromRace(roomCode,request);
    }

    @MessageMapping("/race/{roomCode}/player/left")
    public void handlePlayerLeft(@DestinationVariable String roomCode, StompHeaderAccessor accessor) {
        raceService.playerLeftFromRace(roomCode,accessor);
    }

    @MessageMapping("/race/{roomCode}/player/hint")
    public void handlePlayerAskForHint(@DestinationVariable String roomCode, StompHeaderAccessor accessor) {
        raceService.playerAskForHint(roomCode,accessor);
    }

    @MessageMapping({"/race/{roomCode}/player/change-nickname", "/race/{roomCode}/host/change-nickname"})
    public void handleChangeNickname(@DestinationVariable String roomCode, @Valid @Payload ChangeNicknameRequest request, StompHeaderAccessor accessor) {
        raceService.changeNickname(roomCode,request,accessor);
    }

    @MessageMapping("/race/{roomCode}/host/change-race-name")
    public void handleChangeRaceName(@DestinationVariable String roomCode, @Valid @Payload ChangeRaceNameRequest request, StompHeaderAccessor accessor) {
        raceService.changeRaceName(roomCode,request);
    }

    @MessageMapping("/race/{roomCode}/host/message-to-player")
    public void handleSendMessageToPlayer(@DestinationVariable String roomCode, @Valid @Payload MessageToPlayerRequest request , StompHeaderAccessor accessor) {
        raceService.sendMessageToPlayer(roomCode,request);
    }

    @MessageMapping("/race/{roomCode}/host/pause")
    public void handlePauseRace(@DestinationVariable String roomCode, StompHeaderAccessor accessor) {
        raceService.pauseRace(roomCode);
    }

    @MessageMapping("/race/{roomCode}/host/resume")
    public void handleResumeRace(@DestinationVariable String roomCode, StompHeaderAccessor accessor) {
        raceService.resumeRace(roomCode);
    }

    @MessageMapping("/race/{roomCode}/host/cancel")
    public void handleCancelRace(@DestinationVariable String roomCode, StompHeaderAccessor accessor) {
        raceService.cancelRace(roomCode);
    }

    @MessageMapping({"/race/{roomCode}/host/sync", "/race/{roomCode}/player/sync"})
    public void handleRaceSync(@DestinationVariable String roomCode, StompHeaderAccessor accessor){
        raceService.sendRaceState(roomCode, accessor);
    }

    @MessageMapping("/race.me")
    public void handle(StompHeaderAccessor accessor){
        raceService.checkJoinedRace(accessor);
    }

    @MessageMapping("/race/{roomCode}/player/report-template")
    public void handleReportTemplate(@DestinationVariable String roomCode, @Valid @Payload ReportTemplateRequest request, StompHeaderAccessor accessor) {
        raceService.reportTemplate(roomCode, request, accessor);
    }

    @PostMapping("/create")
    public ApiResponse<CreateRaceResponse> createRace(@Valid @RequestBody CreateRaceRequest request, RequestMetadata metadata) {
        CreateRaceResponse createRaceResponse = raceService.creatRace(request, metadata);
        return ApiResponse.success(createRaceResponse);
    }

    @GetMapping("/public-list")
    public ApiResponse<List<RaceInfoResponse>> getActivePublicRaces(@Valid @ModelAttribute PublicRacesListRequest request, RequestMetadata metadata){
        List<RaceInfoResponse> raceInfoResponse = raceService.getActivePublicRaces(request);
        return ApiResponse.success(raceInfoResponse);
    }

   @PostMapping("/{roomCode}/join")
    public ApiResponse<JoinRaceResponse> joinRace(@PathVariable String roomCode, @Valid @RequestBody JoinRaceRequest request, RequestMetadata metadata){
       if (!StringUtils.hasText(roomCode)) {
           return ApiResponse.error(ErrorCode.INVALID_INPUT);
       }

        JoinRaceResponse joinRaceResponse = raceService.joinRace(roomCode,request,metadata);
        return ApiResponse.success(joinRaceResponse);
    }
}
