package sg.edu.nus.serms.reservation.service;

import sg.edu.nus.serms.shared.domain.RecordNotFoundException;

/** No such reservation (mapped to 404 by the shared handler). */
public class ReservationNotFoundException extends RecordNotFoundException {}
