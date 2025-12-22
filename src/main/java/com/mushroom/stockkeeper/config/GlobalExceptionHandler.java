package com.mushroom.stockkeeper.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ModelAndView handleException(HttpServletRequest request, Exception ex) {
        ModelAndView mav = new ModelAndView(); // You need to create a default error view
        mav.addObject("exception", ex);
        mav.addObject("url", request.getRequestURL());
        mav.addObject("errorMessage", ex.getMessage());
        mav.setViewName("error"); // Assumes error.html exists
        return mav;
    }
}
